package com.example.config;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.util.Collections;
import java.util.Set;

@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
public class PasswordAuthenticationToken extends AbstractAuthenticationToken {
    private final RegisteredClient registeredClient;
    private final Authentication clientPrincipal;
    private final String principal;
    private final String credentials;
    private final Set<String> scopes;

    public PasswordAuthenticationToken(RegisteredClient registeredClient,
                                       Authentication clientPrincipal,
                                       String username,
                                       String password,
                                       Set<String> scopes) {
        super(null);
        this.registeredClient = registeredClient;
        this.clientPrincipal = clientPrincipal;
        this.principal = username;
        this.credentials = password;
        this.scopes = scopes != null ? scopes : Collections.emptySet();
        setAuthenticated(false);
    }
}
