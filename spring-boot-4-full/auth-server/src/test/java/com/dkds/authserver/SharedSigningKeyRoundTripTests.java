package com.dkds.authserver;

import com.dkds.commonsecurity.PemUtils;
import com.dkds.commonsecurity.ResourceServerSecurityConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Phase 6 follow-up: proves the shared-key-pair design actually works end
/// to end, not just that it compiles and the app boots. auth-server signs a
/// real token with its own private key (JwkConfig's `KeyPair` bean); this
/// test decodes it using ONLY the public key packaged inside common-security
/// (`ResourceServerSecurityConfig.PUBLIC_KEY_CLASSPATH_LOCATION`), built the
/// exact same way that module's own `JwtDecoder` bean builds it — this is
/// precisely what any resource server does now, minus the Spring wiring. No
/// JWKS endpoint, no network call, no live auth-server process required for
/// validation to succeed.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Shared signing key: a resource server can validate without JWKS")
class SharedSigningKeyRoundTripTests {

    @Autowired
    private KeyPair keyPair;

    @Test
    @DisplayName("A token signed with auth-server's real key decodes using only the shared public key")
    void tokenSignedWithRealKeyDecodesAgainstSharedPublicKey() throws Exception {
        var token = sign((RSAPrivateKey) keyPair.getPrivate(), "user1");

        var decoder = sharedKeyDecoder();
        var jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("user1");
    }

    @Test
    @DisplayName("A token signed with a different key is rejected — the shared public key actually pins the signer")
    void tokenSignedWithADifferentKeyIsRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var impostorKeyPair = generator.generateKeyPair();
        var token = sign((RSAPrivateKey) impostorKeyPair.getPrivate(), "user1");

        var decoder = sharedKeyDecoder();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private static NimbusJwtDecoder sharedKeyDecoder() {
        var sharedPublicKey = PemUtils.readRSAPublicKey(
                new ClassPathResource(ResourceServerSecurityConfig.PUBLIC_KEY_CLASSPATH_LOCATION));
        return NimbusJwtDecoder.withPublicKey(sharedPublicKey).build();
    }

    private static String sign(RSAPrivateKey privateKey, String subject) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60)))
                .build();
        var signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        signedJwt.sign(new RSASSASigner(privateKey));
        return signedJwt.serialize();
    }
}
