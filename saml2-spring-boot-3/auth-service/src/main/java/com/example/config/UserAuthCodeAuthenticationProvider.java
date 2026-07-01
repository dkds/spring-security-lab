package com.example.config;

import com.example.dto.UserAuthCode;
import com.example.entity.User;
import com.example.exceptions.UserNotFoundException;
import com.example.service.UserService;
import com.example.util.Util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

@RequiredArgsConstructor
public class UserAuthCodeAuthenticationProvider implements AuthenticationProvider {

    private final OAuth2TokenGenerator<OAuth2Token> tokenGenerator;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final TextEncryptor textEncryptor;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        var authCodeAuthToken = (UserAuthCodeAuthenticationToken) authentication;

        var registeredClient = authCodeAuthToken.getRegisteredClient();
        var code = authCodeAuthToken.getCredentials();

        UserAuthCode userAuthCode;
        try {
            var userAuthCodeJson = textEncryptor.decrypt(code);
            userAuthCode = objectMapper.readValue(userAuthCodeJson, UserAuthCode.class);
            userAuthCode.validateOrThrow();
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new OAuth2AuthenticationException("Failed to validate auth code: " + e.getMessage());
        }

        var username = userAuthCode.username();
        var user = (User) userService.loadUserByUsername(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }

        // Authenticate the user
        var userPrincipal = new UsernamePasswordAuthenticationToken(user, null);

        var authorizationServerContext = AuthorizationServerContextHolder.getContext();
        // Generate access token
        @SuppressWarnings("removal")
        var tokenContextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userPrincipal)
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .authorizationGrant(authCodeAuthToken)
                .authorizedScopes(Collections.emptySet())
                .authorizationServerContext(authorizationServerContext);

        var accessTokenContext = tokenContextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
        var oauth2Token = tokenGenerator.generate(accessTokenContext);
        if (oauth2Token == null) {
            throw new OAuth2AuthenticationException("Failed to generate access token");
        }

        var accessToken = getOAuth2AccessToken(oauth2Token, Collections.emptySet());

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
        return UserAuthCodeAuthenticationToken.class.isAssignableFrom(authentication);
    }
}