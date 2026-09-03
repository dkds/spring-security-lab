package com.dkds.commonsecurity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/// The complete, self-contained security chain for a stateless JWT resource
/// server. Meant to be pulled in wholesale (`@Import`) by an application that
/// has NO other security concerns of its own — see AGENTS.md Phase 6 for why
/// auth-server does NOT import this class even though it also validates
/// bearer tokens on its own Chain 2: auth-server already owns three chains of
/// its own, and importing a fourth complete chain here would collide with
/// that (and break Phase 2's "exactly three chains" chain-inventory test).
/// auth-server instead reuses only {@link RolesAndScopesJwtGrantedAuthoritiesConverter}
/// directly.
///
/// Publishes a complete {@code SecurityFilterChain} bean rather than a
/// {@code Customizer<HttpSecurity>} one deliberately: Spring auto-applies
/// every {@code Customizer<HttpSecurity>} bean in the context ambiently to
/// every {@code SecurityFilterChain} being built, which is exactly the wrong
/// shape for something meant to be optionally imported — see PLAN.md Phase 6
/// test 3 ("common-security publishes no Customizer<HttpSecurity> bean
/// carrying a mechanism") and the same footgun already documented on
/// auth-server's FormLoginConfigurer/OneTimeTokenConfigurer.
@Configuration
public class ResourceServerSecurityConfig {

    /// Classpath location of the RSA public key auth-server signs access
    /// tokens with — packaged inside this module's own jar (`src/main/resources/keys`),
    /// so any consumer gets it automatically just by depending on
    /// common-security. Deliberately the PUBLIC key only: the matching
    /// PRIVATE key lives solely in auth-server's own resources and is never
    /// added here — sharing signing capability with a resource server would
    /// let it forge tokens as the authorization server, which defeats the
    /// entire point of the split. See auth-server's JwkConfig for where the
    /// private key is loaded and how the two are kept in sync (the public
    /// key is derived from the private key's own modulus/exponent at
    /// startup, not read from a second copy of this same file, so they can't
    /// drift apart).
    public static final String PUBLIC_KEY_CLASSPATH_LOCATION = "keys/jwt-public-key.pem";

    /// Decodes tokens using the shared public key directly — no network
    /// round-trip to a JWKS endpoint, and no dependency on auth-server being
    /// reachable (or even running) at validation time. `@ConditionalOnMissingBean`
    /// so a consumer can still supply its own `JwtDecoder` (e.g. for a
    /// property-driven `jwk-set-uri` setup) if it has a real reason to.
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        var publicKey = PemUtils.readRSAPublicKey(new ClassPathResource(PUBLIC_KEY_CLASSPATH_LOCATION));
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new RolesAndScopesJwtGrantedAuthoritiesConverter());
        return converter;
    }

    @Bean
    public SecurityFilterChain resourceServerSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
