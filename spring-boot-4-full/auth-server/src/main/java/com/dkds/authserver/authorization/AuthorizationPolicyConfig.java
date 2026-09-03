package com.dkds.authserver.authorization;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationManagerFactory;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.authorization.RequiredAuthoritiesAuthorizationManager;
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
@Configuration
public class AuthorizationPolicyConfig {

    @Bean
    public AuthorizationManagerFactory<RequestAuthorizationContext> authorizationManagerFactory(
            OrgPolicyRequiredAuthoritiesRepository orgPolicyRepository) {
        var factory = new DefaultAuthorizationManagerFactory<RequestAuthorizationContext>();
        factory.setAdditionalAuthorization(new RequiredAuthoritiesAuthorizationManager<>(orgPolicyRepository));
        return factory;
    }
}
