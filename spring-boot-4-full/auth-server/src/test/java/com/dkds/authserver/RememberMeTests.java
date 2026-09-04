package com.dkds.authserver;

import com.dkds.authserver.organization.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Proves the "remember me" checkbox (login.html) actually works end-to-end:
/// a cookie issued at login lets a LATER, session-less request re-authenticate
/// on its own, via the real PersistentTokenBasedRememberMeServices /
/// JpaPersistentTokenRepository wiring — not just that the DSL call compiles.
///
/// Also documents a real, verified boundary: this only works on Chain 3
/// (FormLoginConfigurer). It was ALSO tried on Chain 1 (/oauth2/authorize —
/// exactly where a remembered visitor's first hit actually lands) and
/// reverted after live testing showed it doesn't work there: the OAuth2
/// authorization server registers its own internal
/// OAuth2AuthorizationCodeRequestValidatingFilter well before
/// RememberMeAuthenticationFilter's fixed position in the chain. That filter
/// snapshots the still-unauthenticated principal into a request attribute
/// that OAuth2AuthorizationEndpointFilter later reuses verbatim instead of
/// re-reading SecurityContextHolder — so by the time remember-me actually
/// authenticates, the OAuth2 request has already been validated against an
/// anonymous principal and fails with "OAuth 2.0 Parameter: principal". See
/// SecurityChains' own comment for why this isn't worked around: doing so
/// would mean reordering a private nested class not part of any public API.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Remember-me")
class RememberMeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrgSecurityPolicyRepository orgSecurityPolicyRepository;

    @Test
    @DisplayName("A remember-me cookie alone, on a fresh session-less request, re-authenticates on Chain 3")
    void rememberMeCookieAloneReauthenticatesOnChain3() throws Exception {
        String username = "remember-me-chain3@test";
        seedUser(username);

        var loginResult = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", "correct-password")
                        .param("remember-me", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var rememberMeCookie = loginResult.getResponse().getCookie("remember-me");
        assertThat(rememberMeCookie)
                .as("a successful login with remember-me checked must issue the cookie")
                .isNotNull();

        // /login-success falls under Chain 3's anyRequest().authenticated() —
        // a completely fresh request (no session, only the cookie) must reach
        // it, proving RememberMeAuthenticationFilter/
        // JpaPersistentTokenRepository actually work, not just that the DSL
        // call compiles.
        mockMvc.perform(get("/login-success").cookie(rememberMeCookie))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Known boundary: a remember-me cookie alone does not reach /oauth2/authorize")
    void rememberMeCookieAloneDoesNotReachAuthorize() throws Exception {
        String username = "remember-me-authorize@test";
        seedUser(username);

        var loginResult = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", "correct-password")
                        .param("remember-me", "true")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        var rememberMeCookie = loginResult.getResponse().getCookie("remember-me");
        assertThat(rememberMeCookie).isNotNull();

        String authorizeRequest = "/oauth2/authorize?client_id=spa-client"
                + "&redirect_uri=http://localhost:5173/callback"
                + "&response_type=code"
                + "&scope=openid"
                + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                + "&code_challenge_method=S256";

        var result = mockMvc.perform(get(authorizeRequest).cookie(rememberMeCookie)).andReturn();

        // Documents the actual, verified behavior rather than an assumption:
        // bounced back to the login page, same as a request with no cookie
        // at all — see this class's own javadoc for why.
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl())
                .as("known boundary: the OAuth2 authorization server's own internal filter ordering "
                        + "means remember-me can't satisfy this endpoint — see class javadoc")
                .contains("/login");
    }

    private void seedUser(String username) {
        var user = userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("correct-password"))
                .enabled(true)
                .failedAttempts(0)
                .build());
        var org = organizationRepository.save(Organization.builder()
                .code("REMEMBER-ME-ORG-" + username).name("Remember Me Org").active(true).build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId()).mfaMode(MfaMode.NEVER).ipRestrictionEnabled(false).build());
        membershipRepository.save(Membership.builder()
                .user(user).organization(org).active(true).build());
    }
}
