package com.dkds.authserver.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.*;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/// Publishes the {@link AuthorizationManagerFactory} bean that
/// `AuthorizeHttpRequestsConfigurer` auto-discovers by generic type.
///
/// Per DESIGN.md: `@EnableMultiFactorAuthentication(authorities = {})` with an
/// empty `authorities` array only wires the MFA filter machinery (the OTT
/// login endpoints, the missing-factor entry-point routing) — it does NOT
/// publish this bean, since Spring only imports
/// `AuthorizationManagerFactoryConfiguration` when `authorities` is
/// non-empty. Without a bean here, every `.authenticated()` rule on every
/// chain falls back to a plain `AuthenticatedAuthorizationManager` that knows
/// nothing about factors, and no org-policy MFA requirement is ever enforced.
///
/// Per DESIGN.md Phase 5: `additionalAuthorization` composes the OTT-factor
/// policy with the IP-restriction policy via
/// `AuthorizationManagers.allOf(orgPolicy, ipPolicy)` — both must grant (or
/// abstain) for a request to pass.
///
/// Per DESIGN.md Phase 7: a SAML-authenticated principal satisfies the factor
/// gate on its own, as an alternative to password+OTT — DESIGN.md phrases
/// this as `AllRequiredFactorsAuthorizationManager.anyOf(samlOnly,
/// passwordAndOtt)`. Implemented instead via SamlOrPasswordOttAuthorizationManager,
/// which grants immediately for FACTOR_SAML_RESPONSE and otherwise delegates
/// to the SAME dynamic `ottPolicy` unchanged — see its own javadoc for why:
/// wrapping `ottPolicy` in either `anyOf` helper degrades its denial into a
/// generic composite decision, silently breaking the Phase 4 missing-FACTOR_OTT
/// redirect. IP restriction stays ANDed on the *outside* of that choice
/// (`allOf(factorPolicy, ipPolicy)`), not inside either branch — DESIGN.md:
/// "IP is not a factor", so it applies the same regardless of which factor
/// path satisfied the gate.
@Configuration
public class AuthorizationPolicyConfig {

    @Bean
    public AuthorizationManagerFactory<RequestAuthorizationContext> authorizationManagerFactory(
            OrgPolicyRequiredAuthoritiesRepository orgPolicyRepository,
            OrgIpAuthorizationManager ipAuthorizationManager) {
        var factory = new DefaultAuthorizationManagerFactory<RequestAuthorizationContext>();
        AuthorizationManager<RequestAuthorizationContext> ottPolicy = new RequiredAuthoritiesAuthorizationManager<>(orgPolicyRepository);
        AuthorizationManager<RequestAuthorizationContext> factorPolicy = new SamlOrPasswordOttAuthorizationManager(ottPolicy);
        factory.setAdditionalAuthorization(AuthorizationManagers.allOf(factorPolicy, ipAuthorizationManager));
        return factory;
    }
}
