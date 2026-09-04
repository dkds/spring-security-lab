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
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Phase 7, PLAN.md test 2: "A disabled or unassigned user is rejected on
/// the SAML path too."
///
/// Per DESIGN.md: an assertion only proves the IdP authenticated the user,
/// not that the user is enabled or assigned here — the SAME terminal
/// rejections AppUserDetailsService applies on the password path
/// (disabled/locked/no active membership/password expired) must be applied
/// in the SAML2 response authentication converter, since
/// OpenSaml5AuthenticationProvider never consults UserDetailsService.
///
/// Uses the REAL SamlUserAuthoritiesConverter bean (wired to the real
/// UserService/repositories over H2) with a Mockito-mocked OpenSAML
/// Assertion — the converter only ever calls
/// assertion.getSubject().getNameID().getValue(), so a full signed assertion
/// isn't needed to exercise this logic (that round trip was verified
/// manually against a real Keycloak instance).
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 7: SAML response converter applies the same terminal rejections as the password path")
class Phase7SamlTerminalRejectionTests {

    @Autowired
    private SamlUserAuthoritiesConverter converter;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    @DisplayName("An enabled, actively-membered user gets their real ROLE_* authority")
    void enabledActiveUserGetsRoleAuthority() {
        var user = seedUser("phase7-saml-ok@test", true);
        var org = organizationRepository.save(Organization.builder()
                .code("P7-SAML-OK").name("P7-SAML-OK").active(true).build());
        membershipRepository.save(Membership.builder().user(user).organization(org).active(true).build());

        var authorities = converter.convert(assertionFor("phase7-saml-ok@test"));

        assertThat(authorities)
                .extracting(Object::toString)
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    @DisplayName("A disabled user is rejected on the SAML path, not just the password path")
    void disabledUserIsRejected() {
        var user = seedUser("phase7-saml-disabled@test", false);
        var org = organizationRepository.save(Organization.builder()
                .code("P7-SAML-DISABLED").name("P7-SAML-DISABLED").active(true).build());
        membershipRepository.save(Membership.builder().user(user).organization(org).active(true).build());

        assertThatThrownBy(() -> converter.convert(assertionFor("phase7-saml-disabled@test")))
                .isInstanceOf(DisabledException.class);
    }

    @Test
    @DisplayName("A user with no active membership (unassigned) is rejected on the SAML path")
    void unassignedUserIsRejected() {
        seedUser("phase7-saml-unassigned@test", true);
        // Deliberately no membership row at all.

        assertThatThrownBy(() -> converter.convert(assertionFor("phase7-saml-unassigned@test")))
                .isInstanceOf(AccountExpiredException.class);
    }

    @Test
    @DisplayName("An assertion for an unknown username is rejected, not silently accepted")
    void unknownUsernameIsRejected() {
        assertThatThrownBy(() -> converter.convert(assertionFor("phase7-saml-nobody@test")))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private AppUser seedUser(String username, boolean enabled) {
        return userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash("n/a")
                .enabled(enabled)
                .failedAttempts(0)
                .build());
    }

    private static Assertion assertionFor(String nameId) {
        NameID nameID = mock(NameID.class);
        when(nameID.getValue()).thenReturn(nameId);
        Subject subject = mock(Subject.class);
        when(subject.getNameID()).thenReturn(nameID);
        Assertion assertion = mock(Assertion.class);
        when(assertion.getSubject()).thenReturn(subject);
        return assertion;
    }
}
