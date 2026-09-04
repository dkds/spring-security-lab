package com.dkds.authserver.authorization;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.function.Supplier;

/// Grants immediately for a SAML-authenticated principal
/// (FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY present); otherwise
/// delegates entirely to the wrapped password+OTT policy manager, returning
/// its AuthorizationResult UNCHANGED.
///
/// Deliberately not built from AuthorizationManagers.anyOf(...) (or
/// AllRequiredFactorsAuthorizationManager.anyOf(...), which DESIGN.md's own
/// SAML2 section names): both wrap a denial in their own composite decision
/// object on the "nothing granted" path, which loses the
/// AuthorityAuthorizationDecision/FactorAuthorizationDecision type that
/// DelegatingMissingAuthorityAccessDeniedHandler specifically checks for
/// (verified against its source — see AGENTS.md's Phase 5 note on the same
/// issue for OrgIpAuthorizationManager). The wrapped manager here IS one of
/// those special types today (RequiredAuthoritiesAuthorizationManager over
/// OrgPolicyRequiredAuthoritiesRepository) — that's what routes a principal
/// missing FACTOR_OTT to /ott/request (Phase 4/6). Wrapping it in a generic
/// anyOf(...) would silently break that redirect for every non-SAML request,
/// turning it into a bare 403. Returning the delegate's result verbatim on
/// the non-SAML branch preserves that behavior exactly.
public class SamlOrPasswordOttAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AuthorizationManager<RequestAuthorizationContext> passwordAndOttPolicy;

    public SamlOrPasswordOttAuthorizationManager(
            AuthorizationManager<RequestAuthorizationContext> passwordAndOttPolicy) {
        this.passwordAndOttPolicy = passwordAndOttPolicy;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                          RequestAuthorizationContext context) {
        if (hasSamlFactor(authentication.get())) {
            return new AuthorizationDecision(true);
        }
        return passwordAndOttPolicy.authorize(authentication, context);
    }

    private static boolean hasSamlFactor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY.equals(authority.getAuthority()));
    }
}
