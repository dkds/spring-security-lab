package com.example.config;

import com.example.util.Util;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.HashMap;
import java.util.Set;

@RequiredArgsConstructor
public class PasswordAuthenticationProvider implements AuthenticationProvider {

    private final DaoAuthenticationProvider authenticationProvider;
    private final OAuth2TokenGenerator<OAuth2Token> tokenGenerator;

    @SuppressWarnings("removal")
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var passwordGrantAuth = (PasswordAuthenticationToken) authentication;

        var registeredClient = passwordGrantAuth.getRegisteredClient();
        var username = passwordGrantAuth.getPrincipal();
        var password = passwordGrantAuth.getCredentials();
        var scopes = passwordGrantAuth.getScopes();
        var authorizationServerContext = AuthorizationServerContextHolder.getContext();

        // Authenticate the user
        var userAuthRequest = new UsernamePasswordAuthenticationToken(username, password);
        var userPrincipal = authenticationProvider.authenticate(userAuthRequest);

        if (!userPrincipal.isAuthenticated()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        // Generate access token
        var tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userPrincipal)
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .authorizationGrant(passwordGrantAuth)
                .authorizedScopes(scopes)
                .authorizationServerContext(authorizationServerContext);

        var accessTokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
        var oauth2Token = tokenGenerator.generate(accessTokenContext);
        if (oauth2Token == null) {
            throw new OAuth2AuthenticationException("Failed to generate access token");
        }

        var accessToken = getOAuth2AccessToken(oauth2Token, scopes);

        // Generate refresh token (if allowed)
        OAuth2RefreshToken refreshToken = null;
        if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            var refreshTokenContext = tokenContextBuilder
                    .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                    .build();

            refreshToken = (OAuth2RefreshToken) tokenGenerator.generate(refreshTokenContext);
        }

        var additionalParameters = new HashMap<String, Object>();
        additionalParameters.put(Util.AUTH_REQUEST_PARAM_USERNAME, userPrincipal.getName());

        // Return authentication
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient,
                userPrincipal,
                accessToken,
                refreshToken,
                additionalParameters
        );
    }

    private OAuth2AccessToken getOAuth2AccessToken(OAuth2Token oAuth2Token, Set<String> scopes) {
        OAuth2AccessToken accessToken;
        if (oAuth2Token instanceof OAuth2AccessToken) {
            accessToken = (OAuth2AccessToken) oAuth2Token;
        } else if (oAuth2Token instanceof Jwt jwt) {
            accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    jwt.getTokenValue(),
                    jwt.getIssuedAt(),
                    jwt.getExpiresAt(),
                    scopes
            );
        } else {
            throw new OAuth2AuthenticationException("Unsupported access token type: " + oAuth2Token.getClass().getName());
        }
        return accessToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}