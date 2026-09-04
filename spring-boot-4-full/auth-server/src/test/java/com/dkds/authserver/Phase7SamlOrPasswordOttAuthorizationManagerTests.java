package com.dkds.authserver;

import com.dkds.authserver.authorization.SamlOrPasswordOttAuthorizationManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationDecision;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Pure unit test for the composition risk documented on
/// SamlOrPasswordOttAuthorizationManager itself: a generic
/// AuthorizationManagers.anyOf(...)/AllRequiredFactorsAuthorizationManager.anyOf(...)
/// would wrap a denial from the wrapped policy in its own composite decision
/// type on the "nothing granted" path, losing the
/// AuthorityAuthorizationDecision/FactorAuthorizationDecision type
/// DelegatingMissingAuthorityAccessDeniedHandler specifically checks for
/// (which is what routes a principal missing FACTOR_OTT to /ott/request).
/// This asserts the delegate's own result instance comes back completely
/// unchanged on that path.
@DisplayName("Phase 7: SamlOrPasswordOttAuthorizationManager")
class Phase7SamlOrPasswordOttAuthorizationManagerTests {

    @Test
    @DisplayName("Grants immediately for a principal carrying FACTOR_SAML_RESPONSE, without consulting the delegate")
    void grantsForSamlFactorWithoutConsultingDelegate() {
        AuthorizationManager<RequestAuthorizationContext> delegate = mock(AuthorizationManager.class);
        var manager = new SamlOrPasswordOttAuthorizationManager(delegate);

        var samlAuthentication = new TestingAuthenticationToken("ssouser@dkds.com", "n/a", List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY)));
        samlAuthentication.setAuthenticated(true);

        AuthorizationResult result = manager.authorize(() -> samlAuthentication, mock(RequestAuthorizationContext.class));

        assertThat(result.isGranted()).isTrue();
    }

    @Test
    @DisplayName("Without FACTOR_SAML_RESPONSE, returns the delegate's own AuthorizationResult instance UNCHANGED")
    void delegatesVerbatimWithoutSamlFactor() {
        var passwordOnlyAuthentication = new TestingAuthenticationToken("user2@dkds.com", "n/a", List.of(
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)));
        passwordOnlyAuthentication.setAuthenticated(true);

        // The exact type OrgPolicyRequiredAuthoritiesRepository's real
        // manager (RequiredAuthoritiesAuthorizationManager) produces on
        // denial — DelegatingMissingAuthorityAccessDeniedHandler routes on
        // this specific type.
        AuthorityAuthorizationDecision missingOttDecision =
                new AuthorityAuthorizationDecision(false, List.of(new SimpleGrantedAuthority(FactorGrantedAuthority.OTT_AUTHORITY)));

        AuthorizationManager<RequestAuthorizationContext> delegate = mock(AuthorizationManager.class);
        when(delegate.authorize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(missingOttDecision);

        var manager = new SamlOrPasswordOttAuthorizationManager(delegate);

        AuthorizationResult result = manager.authorize(() -> passwordOnlyAuthentication, mock(RequestAuthorizationContext.class));

        assertThat(result)
                .as("must be the delegate's exact result instance, not a rewrapped/composite decision")
                .isSameAs(missingOttDecision);
        assertThat(result).isInstanceOf(AuthorityAuthorizationDecision.class);
    }

    @Test
    @DisplayName("An unauthenticated (anonymous) supplier is not treated as carrying the SAML factor")
    void unauthenticatedIsNotTreatedAsSaml() {
        AuthorizationDecision delegateDecision = new AuthorizationDecision(true);
        AuthorizationManager<RequestAuthorizationContext> delegate = mock(AuthorizationManager.class);
        when(delegate.authorize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(delegateDecision);

        var manager = new SamlOrPasswordOttAuthorizationManager(delegate);

        AuthorizationResult result = manager.authorize(() -> null, mock(RequestAuthorizationContext.class));

        assertThat(result).isSameAs(delegateDecision);
    }
}
