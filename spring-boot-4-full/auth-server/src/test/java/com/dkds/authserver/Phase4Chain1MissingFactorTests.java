package com.dkds.authserver;

import com.dkds.authserver.onetimetoken.OneTimeTokenRepository;
import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Phase 4 regression test: a principal missing FACTOR_OTT on /oauth2/authorize
/// (Chain 1) must be redirected into the OTT flow, not answered with a bare
/// 403 — and that redirect must land somewhere that can actually get them a
/// code, not back on the username/password form.
///
/// Two bugs, found in sequence against a real running instance:
/// 1. OneTimeTokenConfigurer registers the missing-FACTOR_OTT routing
///    automatically, but only on the HttpSecurity it's applied to (Chain 3).
///    Chain 1 — where /oauth2/authorize actually lives, and where the
///    composed AuthorizationManagerFactory first denies a principal missing
///    FACTOR_OTT — never carried that registration until SecurityChains
///    wired its own defaultDeniedHandlerForMissingAuthority for it.
/// 2. That routing initially pointed at SecurityConstants.LOGIN_PAGE, which
///    only renders a username/password form. A principal who lands there
///    missing FACTOR_OTT has already authenticated with a password;
///    resubmitting that form just re-authenticates and loops back to the
///    same denial. It now points at EmailOttDeliveryHandler.OTT_REQUEST_URL,
///    a dedicated page that requests a code for the already-authenticated
///    principal.
/// 3. Introducing that OTT_REQUEST_URL as OneTimeTokenConfigurer's
///    loginPage(...) had a side effect: loginPage(...) internally calls
///    updateAuthenticationDefaults(), which — finding the processing URL
///    still unset — virtually dispatches into this configurer's own
///    loginProcessingUrl(...) override and reassigns
///    OneTimeTokenAuthenticationFilter's actual matcher to OTT_REQUEST_URL
///    instead of the real submission endpoint /login/ott. Codes typed on
///    /ott/input (which posts to /login/ott per that template) then fell
///    through the filter chain completely unvalidated and got denied. Fixed
///    by pinning loginProcessingUrl(...) explicitly, after loginPage(...).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: Chain 1 missing-factor routing")
class Phase4Chain1MissingFactorTests {

    private static final String USERNAME = "phase4-chain1@test";
    private static final String PASSWORD = "testpass123";
    private static final String AUTHORIZE_REQUEST =
            "/oauth2/authorize?client_id=spa-client"
                    + "&redirect_uri=http://localhost:5173/callback"
                    + "&response_type=code"
                    + "&scope=openid"
                    + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                    + "&code_challenge_method=S256";

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

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Test
    @DisplayName("Password-only session missing FACTOR_OTT is routed all the way to a real code request, not 403 or a login loop")
    void missingOttOnAuthorizeRoutesToOttRequestNotLoginOr403() throws Exception {
        var user = userRepository.save(AppUser.builder()
                .username(USERNAME)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .failedAttempts(0)
                .build());
        var org = organizationRepository.save(Organization.builder()
                .code("P4-CHAIN1-DAILY")
                .name("P4-CHAIN1-DAILY")
                .active(true)
                .build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId())
                .mfaMode(MfaMode.DAILY)
                .ipRestrictionEnabled(false)
                .build());
        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .active(true)
                .build());
        // No user_verification row at all: FACTOR_OTT is required and unmet.

        MvcResult loginPage = mockMvc.perform(get(AUTHORIZE_REQUEST))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var session = (MockHttpSession) loginPage.getRequest().getSession(false);
        assertThat(session).isNotNull();

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .session(session)
                        .param("username", USERNAME)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var redirectAfterLogin = loginResult.getResponse().getRedirectedUrl();
        assertThat(redirectAfterLogin).contains("/oauth2/authorize");

        var authorizeUri = URI.create(redirectAfterLogin);
        MvcResult authorizeResult = mockMvc.perform(get(authorizeUri).session(session))
                .andReturn();

        // Bug 1: this used to be 403 (bare AccessDeniedHandler) instead of a
        // redirect back into the OTT flow.
        assertThat(authorizeResult.getResponse().getStatus())
                .as("missing FACTOR_OTT on Chain 1 must redirect, not 403")
                .isIn(301, 302, 303, 307, 308);
        var redirectAfterAuthorize = authorizeResult.getResponse().getRedirectedUrl();
        // Bug 2: this used to be /login, which only offers a password form —
        // useless (and loop-inducing) for a principal that's already
        // authenticated and just needs an OTT code.
        assertThat(redirectAfterAuthorize)
                .as("must land on the OTT request page, not back on the login form")
                .contains("/ott/request");

        mockMvc.perform(get("/ott/request").session(session))
                .andExpect(status().isOk());

        MvcResult generateResult = mockMvc.perform(post("/ott/generate")
                        .session(session)
                        .param("username", USERNAME)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(generateResult.getResponse().getRedirectedUrl())
                .as("requesting a code must hand off to the code-entry screen")
                .contains("/ott/input");

        // Bug 3: submitting the real code used to fall through the filter
        // chain entirely unvalidated (wrong matcher URL) and get denied,
        // landing back on /ott/request instead of resuming the saved
        // /oauth2/authorize request.
        var code = oneTimeTokenRepository.findByUsername(USERNAME).orElseThrow().getTokenValue();
        MvcResult submitResult = mockMvc.perform(post("/login/ott")
                        .session(session)
                        .param("token", code)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(submitResult.getResponse().getRedirectedUrl())
                .as("a correct code must resume the saved /oauth2/authorize request, not bounce back to /ott/request")
                .contains("/oauth2/authorize");
    }
}
