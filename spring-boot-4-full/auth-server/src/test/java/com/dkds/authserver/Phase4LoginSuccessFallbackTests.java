package com.dkds.authserver;

import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.security.SecurityConstants;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Phase 4 regression test: a form login with nothing in the request cache
/// to resume (no prior /oauth2/authorize hit in this session — e.g. a
/// principal navigating straight to /login, or logging in again right after
/// a logout that invalidated the session and its saved request) used to
/// redirect to "/", which this authorization-server-only app never maps —
/// AuthServerApplication has no root controller, so the browser landed on
/// Spring Boot's Whitelabel /error page with a 404. Found via live manual
/// testing: log out from the SPA, then log back in directly at /login
/// without going through the SPA's /oauth2/authorize redirect first.
///
/// FormLoginConfigurer now sets defaultSuccessUrl(LOGIN_SUCCESS_URL)
/// (alwaysUse=false, so a real saved request — the normal SPA flow — still
/// wins and gets resumed exactly as before; this only changes the fallback).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: form login falls back to /login-success, not /")
class Phase4LoginSuccessFallbackTests {

    private static final String USERNAME = "phase4-login-success@test";
    private static final String PASSWORD = "testpass123";

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
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Login with no saved request redirects to /login-success, not /")
    void loginWithNoSavedRequestRedirectsToLoginSuccess() throws Exception {
        var user = userRepository.save(AppUser.builder()
                .username(USERNAME)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .failedAttempts(0)
                .build());
        // AppUserDetailsService maps "no active membership" to accountExpired
        // (a terminal rejection, per DESIGN.md) - a membership is required
        // just to get past primary authentication, regardless of this test's
        // actual concern (the post-login redirect target).
        var org = organizationRepository.save(Organization.builder()
                .code("P4-LOGIN-SUCCESS")
                .name("P4-LOGIN-SUCCESS")
                .active(true)
                .build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(false)
                .build());
        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .active(true)
                .build());

        var result = mockMvc.perform(post("/login")
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl())
                .as("with nothing saved to resume, login must land on the real fallback page, not /")
                .isEqualTo(SecurityConstants.LOGIN_SUCCESS_URL);
    }
}
