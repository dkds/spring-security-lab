package com.example.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAuthCodeAuthenticationConverter implements AuthenticationConverter {

    public static final String GRANT_TYPE_CODE_EXCHANGE = "code_exchange";
    private final RegisteredClientRepository registeredClientRepository;

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!GRANT_TYPE_CODE_EXCHANGE.equals(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))) {
            return null;
        }

        var clientPrincipal = SecurityContextHolder.getContext().getAuthentication();
        if (!(clientPrincipal instanceof OAuth2ClientAuthenticationToken authToken)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        var tokenClient = authToken.getRegisteredClient();
        if (tokenClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        var registeredClient = registeredClientRepository.findByClientId(tokenClient.getClientId());
        if (registeredClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        String code = request.getParameter(OAuth2ParameterNames.CODE);

        return new UserAuthCodeAuthenticationToken(registeredClient, clientPrincipal, code);
    }
}
