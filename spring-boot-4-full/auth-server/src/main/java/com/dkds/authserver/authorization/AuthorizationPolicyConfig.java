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
/// Per DESIGN.md Phase 7/10: a SAML-authenticated principal goes through this
/// SAME dynamic ottPolicy check too — not a separate unconditional bypass.
/// Phase 7 originally special-cased FACTOR_SAML_RESPONSE to grant
/// immediately (matching DESIGN.md's literal
/// `AllRequiredFactorsAuthorizationManager.anyOf(samlOnly, passwordAndOtt)`
/// phrasing) via a dedicated SamlOrPasswordOttAuthorizationManager wrapper.
/// Phase 10 removed that wrapper deliberately: under the literal reading,
/// any SAML login bypassed an org's own MFA-interval policy unconditionally,
/// which made Phase 10's own stated test ("an org whose IdP asserts MFA
/// satisfies the requirement without an OTT prompt") trivially true whether
/// or not the IdP actually asserted MFA — not a real behavioral proof of
/// anything. With the wrapper gone, ottPolicy's existing verified_at-freshness
/// check (see OrgPolicyRequiredAuthoritiesRepository) applies uniformly to
/// every mechanism; SamlUserAuthoritiesConverter granting
/// FactorGrantedAuthority.fromFactor("IDP_MFA") when the assertion asserts
/// MFA, and LoginRecordingListener writing user_verification.verified_at for
/// either FACTOR_OTT or FACTOR_IDP_MFA, is what actually makes an
/// MFA-asserting IdP skip the OTT prompt — genuinely conditional on what the
/// IdP asserted, not a blanket exemption for the mechanism.
///
/// IP restriction stays ANDed on the outside (`allOf(ottPolicy, ipPolicy)`),
/// applying the same regardless of which factor satisfied the gate —
/// DESIGN.md: "IP is not a factor".
@Configuration
public class AuthorizationPolicyConfig {

    @Bean
    public AuthorizationManagerFactory<RequestAuthorizationContext> authorizationManagerFactory(
            OrgPolicyRequiredAuthoritiesRepository orgPolicyRepository,
            OrgIpAuthorizationManager ipAuthorizationManager) {
        var factory = new DefaultAuthorizationManagerFactory<RequestAuthorizationContext>();
        AuthorizationManager<RequestAuthorizationContext> ottPolicy = new RequiredAuthoritiesAuthorizationManager<>(orgPolicyRepository);
        factory.setAdditionalAuthorization(AuthorizationManagers.allOf(ottPolicy, ipAuthorizationManager));
        return factory;
    }
}
