package com.example.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
public class UserAuthCodeAuthenticationToken extends AbstractAuthenticationToken {
    private final RegisteredClient registeredClient;
    private final Authentication clientPrincipal;
    private final String principal;
    private final String credentials;

    public UserAuthCodeAuthenticationToken(RegisteredClient registeredClient,
                                           Authentication clientPrincipal,
                                           String credentials) {
        super(null);
        this.registeredClient = registeredClient;
        this.clientPrincipal = clientPrincipal;
        this.principal = null;
        this.credentials = credentials;
        setAuthenticated(false);
    }
}
