package com.dkds.authserver;

import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.sso.IdentityProvider;
import com.dkds.authserver.sso.IdentityProviderRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/// SsoDiscoveryController: username-based SSO org discovery/picker.
///
/// Covers the flow described when this feature was added: a user enters
/// their username, sees the SSO-eligible orgs from their own active
/// memberships, and picks one to be redirected to that org's IdP. Always
/// shows the picker screen, even for a single match (no auto-redirect).
///
/// The negative tests are the substantive ones: an unknown username and a
/// known username with no SSO-eligible membership must render the SAME
/// generic empty result — this endpoint is necessarily reachable
/// pre-authentication, so it must not become a username-enumeration oracle.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 7: SSO org discovery/picker")
class Phase7SsoDiscoveryTests {

    private static final String NO_SSO_MARKER = "No SSO sign-in available";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrgSecurityPolicyRepository orgSecurityPolicyRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Test
    @DisplayName("A user with an active membership in an SSO-configured org sees that org listed")
    void listsSsoEligibleOrgForActiveMembership() throws Exception {
        var user = seedUser("phase7-discover-ok@test");
        var org = seedOrg("P7-DISCOVER-OK");
        seedMembership(user, org);
        identityProviderRepository.save(IdentityProvider.builder()
                .registrationId("phase7-discover-ok-idp")
                .orgId(org.getId())
                .entityId("http://idp.test.invalid/realms/discover-ok")
                .ssoUrl("http://idp.test.invalid/realms/discover-ok/protocol/saml")
                .certificate("dummy-cert")
                .active(true)
                .build());

        mockMvc.perform(post("/sso/discover")
                        .param("username", "phase7-discover-ok@test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-select-org"))
                .andExpect(content().string(Matchers.containsString(org.getName())))
                .andExpect(content().string(Matchers.containsString("/saml2/authenticate/phase7-discover-ok-idp")));
    }

    @Test
    @DisplayName("A user with no membership in any SSO-configured org gets the generic empty result")
    void noSsoOrgYieldsGenericEmptyResult() throws Exception {
        var user = seedUser("phase7-discover-nosso@test");
        var org = seedOrg("P7-DISCOVER-NOSSO");
        seedMembership(user, org);
        // No identity_provider row for this org at all.

        mockMvc.perform(post("/sso/discover")
                        .param("username", "phase7-discover-nosso@test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("sso-select-org"))
                .andExpect(content().string(Matchers.containsString(NO_SSO_MARKER)));
    }

    @Test
    @DisplayName("An unknown username gets the exact same rendered result as a known-but-no-SSO username — no enumeration")
    void unknownUsernameYieldsSameGenericResult() throws Exception {
        var user = seedUser("phase7-discover-nosso2@test");
        var org = seedOrg("P7-DISCOVER-NOSSO2");
        seedMembership(user, org);

        var knownButNoSsoBody = mockMvc.perform(post("/sso/discover")
                        .param("username", "phase7-discover-nosso2@test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var unknownUsernameBody = mockMvc.perform(post("/sso/discover")
                        .param("username", "phase7-discover-does-not-exist@test")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUsernameBody)
                .as("an unknown username must render byte-for-byte identically to a known username with no SSO org")
                .isEqualTo(knownButNoSsoBody);
        assertThat(unknownUsernameBody).contains(NO_SSO_MARKER);
    }

    private AppUser seedUser(String username) {
        return userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash("n/a")
                .enabled(true)
                .failedAttempts(0)
                .build());
    }

    private Organization seedOrg(String code) {
        var org = organizationRepository.save(Organization.builder()
                .code(code).name(code).active(true).build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId()).mfaMode(MfaMode.NEVER).ipRestrictionEnabled(false).build());
        return org;
    }

    private void seedMembership(AppUser user, Organization org) {
        membershipRepository.save(Membership.builder().user(user).organization(org).active(true).build());
    }
}
