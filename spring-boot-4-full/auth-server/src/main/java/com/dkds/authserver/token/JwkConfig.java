package com.dkds.authserver.token;

import com.dkds.commonsecurity.PemUtils;
import com.dkds.commonsecurity.ResourceServerSecurityConfig;
import com.dkds.commonsecurity.RolesAndScopesJwtGrantedAuthoritiesConverter;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.UUID;

/// JWT and JWK configuration for the authorization server.
@Configuration
@RequiredArgsConstructor
public class JwkConfig {

    private static final String PRIVATE_KEY_CLASSPATH_LOCATION = "keys/jwt-signing-key.pem";

    /// Fixed RSA key pair, loaded from a checked-in PEM file, rather than
    /// randomly generated on every boot.
    ///
    /// This is a deliberate change from the original random-per-boot
    /// generation: a random key meant every auth-server restart silently
    /// invalidated every previously-issued token and rotated the signing key
    /// out from under any resource server that had cached the old JWKS
    /// response — and now that resource servers validate against a shared
    /// public key instead of a live JWKS fetch (see common-security's
    /// ResourceServerSecurityConfig), there's no discovery mechanism to pick
    /// up a rotated key anyway.
    ///
    /// The public half is shared via common-security's own copy of this same
    /// key (PUBLIC_KEY_CLASSPATH_LOCATION, packaged inside that module's
    /// jar) — but it is deliberately NOT read from there here. Deriving the
    /// public key from THIS private key's own CRT parameters instead, and
    /// verifying the result matches common-security's copy at startup
    /// (verifySharedPublicKeyMatches), makes the two files structurally
    /// unable to silently drift apart: if someone regenerates one without
    /// the other, this fails fast at boot instead of resource-server quietly
    /// rejecting every real token.
    ///
    /// NOTE: a checked-in private key is a POC/demo-only shortcut — never
    /// acceptable for a real deployment. See AGENTS.md Known Gaps.
    @Bean
    public KeyPair keyPair() throws GeneralSecurityException {
        var privateKey = PemUtils.readRSAPrivateKey(new ClassPathResource(PRIVATE_KEY_CLASSPATH_LOCATION));
        var publicKey = derivePublicKey(privateKey);
        verifySharedPublicKeyMatches(publicKey);
        return new KeyPair(publicKey, privateKey);
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws GeneralSecurityException {
        if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
            throw new IllegalStateException(
                    "RSA private key at " + PRIVATE_KEY_CLASSPATH_LOCATION + " lacks CRT parameters; "
                            + "cannot derive its public key");
        }
        var keyFactory = KeyFactory.getInstance("RSA");
        var spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private static void verifySharedPublicKeyMatches(RSAPublicKey derivedPublicKey) {
        var sharedPublicKey = PemUtils.readRSAPublicKey(
                new ClassPathResource(ResourceServerSecurityConfig.PUBLIC_KEY_CLASSPATH_LOCATION));
        boolean matches = derivedPublicKey.getModulus().equals(sharedPublicKey.getModulus())
                && derivedPublicKey.getPublicExponent().equals(sharedPublicKey.getPublicExponent());
        if (!matches) {
            throw new IllegalStateException(
                    "auth-server's signing key (" + PRIVATE_KEY_CLASSPATH_LOCATION + ") does not match "
                            + "common-security's shared public key (" + ResourceServerSecurityConfig.PUBLIC_KEY_CLASSPATH_LOCATION
                            + ") — every resource server validating against the shared key would reject every "
                            + "token this app issues. Regenerate both files together.");
        }
    }

    /// Build RSAKey from KeyPair for JWK Set.
    @Bean
    public RSAKey rsaKey(KeyPair keyPair) {
        var publicKey = (RSAPublicKey) keyPair.getPublic();
        var privateKey = (RSAPrivateKey) keyPair.getPrivate();

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    /// Create JWK Source for signing tokens.
    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new com.nimbusds.jose.jwk.JWKSet(rsaKey));
    }

    /// JWT decoder using the RSA public key from this server.
    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    /// Shared with every resource server that validates tokens this app
    /// issues (see common-security) — Chain 2 (`/api/**`) uses this same
    /// converter on its own access tokens, rather than Spring's SCOPE_-only
    /// default, so ROLE_* authorities (from AccessTokenCustomizer's "roles"
    /// claim) are available for authorization decisions here too.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RolesAndScopesJwtGrantedAuthoritiesConverter());
        return converter;
    }
}
