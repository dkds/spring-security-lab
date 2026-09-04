package com.dkds.authserver;

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

/// End-to-end proof that CaptchaFilter, CaptchaService and
/// LoginAttemptRecordingListener actually work together: enough recent
/// failures for this username+IP trip the gate, a correct-credentials retry
/// without a captcha token is still blocked (never reaches
/// UsernamePasswordAuthenticationFilter at all), and supplying a token lets
/// it through.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 9: CaptchaFilter end-to-end")
class Phase9CaptchaFilterEndToEndTests {

    private static final String USERNAME = "phase9-e2e@test";
    private static final String PASSWORD = "correct-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.dkds.authserver.user.UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.dkds.authserver.organization.OrganizationRepository organizationRepository;

    @Autowired
    private com.dkds.authserver.organization.MembershipRepository membershipRepository;

    @Autowired
    private com.dkds.authserver.organization.OrgSecurityPolicyRepository orgSecurityPolicyRepository;

    @Test
    @DisplayName("After enough recent failures, even correct credentials are blocked without a captcha token, and let through with one")
    void captchaGateBlocksThenAllowsWithToken() throws Exception {
        var user = userRepository.save(com.dkds.authserver.user.AppUser.builder()
                .username(USERNAME)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .failedAttempts(0)
                .build());
        // AppUserDetailsService rejects a user with no active membership as
        // AccountExpired — unrelated to captcha gating, but needed for the
        // final "let through" step to actually succeed rather than fail for
        // a completely different reason.
        var org = organizationRepository.save(com.dkds.authserver.organization.Organization.builder()
                .code("P9-CAPTCHA").name("P9-CAPTCHA").active(true).build());
        orgSecurityPolicyRepository.save(com.dkds.authserver.organization.OrgSecurityPolicy.builder()
                .orgId(org.getId()).mfaMode(com.dkds.authserver.organization.MfaMode.NEVER).ipRestrictionEnabled(false).build());
        membershipRepository.save(com.dkds.authserver.organization.Membership.builder()
                .user(user).organization(org).active(true).build());

        // Three failures — under the threshold, ordinary bad-credentials
        // rejection each time, not yet gated by the filter.
        for (int i = 0; i < 3; i++) {
            var result = mockMvc.perform(post("/login")
                            .param("username", USERNAME)
                            .param("password", "wrong-password")
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            assertThat(result.getResponse().getRedirectedUrl())
                    .as("attempt %d should be an ordinary bad-credentials rejection, not the captcha gate", i + 1)
                    .doesNotContain("captcha");
        }

        // Now at the threshold: even CORRECT credentials are blocked, since
        // the gate runs before UsernamePasswordAuthenticationFilter ever
        // checks the password.
        var gated = mockMvc.perform(post("/login")
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(gated.getResponse().getRedirectedUrl())
                .as("correct credentials must still be gated once the recent-failure threshold is met")
                .contains("captcha");

        // Supplying a (lab-appropriate, non-blank) captcha token lets the
        // real authentication attempt through.
        var passed = mockMvc.perform(post("/login")
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .param("captchaToken", "solved")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(passed.getResponse().getRedirectedUrl())
                .as("a supplied captcha token must let a correct login through, all the way to a real success redirect")
                .isEqualTo(com.dkds.authserver.security.SecurityConstants.LOGIN_SUCCESS_URL);
    }
}
