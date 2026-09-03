package com.dkds.authserver.token;

import com.dkds.authserver.security.SecurityConstants;
import com.dkds.commonsecurity.RolesAndScopesJwtGrantedAuthoritiesConverter;
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
/// kept disjoint. We therefore expose ONLY role authorities (ROLE_ prefix),
/// under a dedicated "roles" claim (RolesAndScopesJwtGrantedAuthoritiesConverter.ROLES_CLAIM_NAME)
/// with the prefix stripped. FACTOR_ and PERM_ authorities are intentionally
/// NOT surfaced here.
///
/// Written to BOTH the ID token (the SPA's own claims-based UI reads it
/// directly) and the ACCESS token (Phase 6: this is what a resource server —
/// or auth-server's own Chain 2 — actually receives as a Bearer token and
/// validates; before Phase 6 the access token carried no role information at
/// all, only the standard registered claims plus whatever scopes were
/// requested). RolesAndScopesJwtGrantedAuthoritiesConverter, shared via the
/// common-security module, is what turns this claim back into ROLE_*
/// authorities on the consuming side — the claim name here and the claim
/// name it reads for must match, which is exactly why both sides reference
/// the same constant instead of each hardcoding the string "roles".
@Component
@Slf4j
public class AccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        var principal = context.getPrincipal();
        var tokenTypeValue = context.getTokenType().getValue();
        boolean isIdToken = OidcParameterNames.ID_TOKEN.equals(tokenTypeValue);
        boolean isAccessToken = OAuth2TokenType.ACCESS_TOKEN.getValue().equals(tokenTypeValue);

        if (isIdToken || isAccessToken) {
            List<String> roles = principal.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a.startsWith(SecurityConstants.ROLE_PREFIX))
                    .map(a -> a.substring(SecurityConstants.ROLE_PREFIX.length()))
                    .toList();

            context.getClaims().claim(RolesAndScopesJwtGrantedAuthoritiesConverter.ROLES_CLAIM_NAME, roles);
            log.debug("Added {} role(s) to {} for principal {}", roles.size(), tokenTypeValue, principal.getName());
        }
    }
}
