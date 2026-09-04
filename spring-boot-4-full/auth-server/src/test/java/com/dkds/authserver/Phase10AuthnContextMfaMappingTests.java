package com.dkds.authserver;

import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.sso.SamlUserAuthoritiesConverter;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnContext;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Phase 10: "Map AuthnContextClassRef onto FACTOR_IDP_MFA in the SAML2
/// response converter." Unit-level against SamlUserAuthoritiesConverter's
/// real assertsMfa(...) logic, same Mockito-based approach as
/// Phase7SamlTerminalRejectionTests — no live-signed assertion needed since
/// none of this depends on cryptographic validity, only on the assertion's
/// own AuthnStatement/AuthnContext/AuthnContextClassRef structure.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 10: AuthnContextClassRef -> FACTOR_IDP_MFA mapping")
class Phase10AuthnContextMfaMappingTests {

    @Autowired
    private SamlUserAuthoritiesConverter converter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    @DisplayName("An assertion whose AuthnContextClassRef names a known MFA class grants FACTOR_IDP_MFA")
    void mfaAuthnContextGrantsIdpMfaFactor() {
        seedUserWithMembership("phase10-mfa-ref@test");

        var authorities = converter.convert(
                assertionFor("phase10-mfa-ref@test", "urn:oasis:names:tc:SAML:2.0:ac:classes:TimeSyncToken"));

        assertThat(authorities)
                .extracting(a -> a.getAuthority())
                .contains(SamlUserAuthoritiesConverter.IDP_MFA_AUTHORITY);
    }

    @Test
    @DisplayName("An assertion with the default 'unspecified' AuthnContextClassRef does NOT grant FACTOR_IDP_MFA")
    void unspecifiedAuthnContextDoesNotGrantIdpMfaFactor() {
        seedUserWithMembership("phase10-no-mfa-ref@test");

        var authorities = converter.convert(
                assertionFor("phase10-no-mfa-ref@test", "urn:oasis:names:tc:SAML:2.0:ac:classes:unspecified"));

        assertThat(authorities)
                .extracting(a -> a.getAuthority())
                .doesNotContain(SamlUserAuthoritiesConverter.IDP_MFA_AUTHORITY);
    }

    @Test
    @DisplayName("An assertion with no AuthnStatement at all does NOT grant FACTOR_IDP_MFA")
    void noAuthnStatementDoesNotGrantIdpMfaFactor() {
        seedUserWithMembership("phase10-no-statement@test");

        var authorities = converter.convert(assertionFor("phase10-no-statement@test", null));

        assertThat(authorities)
                .extracting(a -> a.getAuthority())
                .doesNotContain(SamlUserAuthoritiesConverter.IDP_MFA_AUTHORITY);
    }

    private void seedUserWithMembership(String username) {
        var user = userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash("n/a")
                .enabled(true)
                .failedAttempts(0)
                .build());
        var org = organizationRepository.save(Organization.builder()
                .code(username).name(username).active(true).build());
        membershipRepository.save(Membership.builder().user(user).organization(org).active(true).build());
    }

    /// authnContextClassRefUri == null means "no AuthnStatement at all" (the
    /// common case for a plain, non-MFA assertion), matching what Keycloak's
    /// realm actually emits in the manually-verified live flow.
    private static Assertion assertionFor(String nameId, String authnContextClassRefUri) {
        NameID nameID = mock(NameID.class);
        when(nameID.getValue()).thenReturn(nameId);
        Subject subject = mock(Subject.class);
        when(subject.getNameID()).thenReturn(nameID);
        Assertion assertion = mock(Assertion.class);
        when(assertion.getSubject()).thenReturn(subject);

        if (authnContextClassRefUri == null) {
            when(assertion.getAuthnStatements()).thenReturn(List.of());
        } else {
            AuthnContextClassRef classRef = mock(AuthnContextClassRef.class);
            when(classRef.getURI()).thenReturn(authnContextClassRefUri);
            AuthnContext authnContext = mock(AuthnContext.class);
            when(authnContext.getAuthnContextClassRef()).thenReturn(classRef);
            AuthnStatement statement = mock(AuthnStatement.class);
            when(statement.getAuthnContext()).thenReturn(authnContext);
            when(assertion.getAuthnStatements()).thenReturn(List.of(statement));
        }
        return assertion;
    }
}
