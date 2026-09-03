package com.dkds.authserver.token;

import com.dkds.authserver.security.SecurityConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;

/// Customizes tokens issued by the authorization server.
///
/// Per DESIGN.md the three authority namespaces (ROLE_, PERM_, FACTOR_) must be
/// kept disjoint. We therefore expose ONLY role authorities (ROLE_ prefix) in
/// the ID token, under a dedicated "roles" claim with the prefix stripped.
/// FACTOR_ and PERM_ authorities are intentionally NOT surfaced here.
@Component
@Slf4j
public class AccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        var principal = context.getPrincipal();

        // Only enrich the ID token; access-token contents are driven by scopes.
        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            List<String> roles = principal.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a.startsWith(SecurityConstants.ROLE_PREFIX))
                    .map(a -> a.substring(SecurityConstants.ROLE_PREFIX.length()))
                    .toList();

            context.getClaims().claim("roles", roles);
            log.debug("Added {} role(s) to ID token for principal {}", roles.size(), principal.getName());
        }
    }
}
