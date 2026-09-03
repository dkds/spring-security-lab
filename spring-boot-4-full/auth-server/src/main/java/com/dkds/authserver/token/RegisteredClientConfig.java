package com.dkds.authserver.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

/// OAuth2 registered client and authorization server settings.
///
/// Phase 2: single public SPA client using the authorization code flow with
/// PKCE. No client secret; PKCE is mandatory.
///
/// NOTE (DESIGN.md non-obvious rule): a trailing line comment after a builder
/// call can silently swallow the next chained call. All builder chains below
/// are kept comment-free between calls for this reason.
@Configuration
public class RegisteredClientConfig {

    private final String redirectUri;
    private final String issuer;

    public RegisteredClientConfig(
            @Value("${app.oauth2.spa-redirect-uri:http://localhost:5173/callback}") String redirectUri,
            @Value("${app.oauth2.issuer:http://localhost:9000}") String issuer) {
        this.redirectUri = redirectUri;
        this.issuer = issuer;
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        var clientSettings = ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(false)
                .build();

        var tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(15))
                .refreshTokenTimeToLive(Duration.ofDays(7))
                .reuseRefreshTokens(true)
                .build();

        var spaClient = RegisteredClient
                .withId("spa-client")
                .clientId("spa-client")
                .clientName("SPA Application")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .scope("api")
                .clientSettings(clientSettings)
                .tokenSettings(tokenSettings)
                .build();

        return new InMemoryRegisteredClientRepository(spaClient);
    }

    /// Authorization server settings. The issuer must resolve to a URL the SPA
    /// and resource server can reach. Endpoint paths keep Spring's defaults.
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }
}
