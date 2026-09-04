package com.dkds.authserver;

import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.sso.SamlUserAuthoritiesConverter;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/// Phase 10, PLAN.md's own test verbatim: "an organisation whose IdP asserts
/// MFA satisfies the requirement without an OTT prompt."
///
/// This is the test that makes Phase 10 mean something concrete, unlike
/// Phase7SamlFactorSatisfiesGateTests (which now only proves a NEVER-mode
/// org doesn't ask for anything, regardless of mechanism). Uses a DAILY-mode
/// org with no user_verification row at all, so the dynamic policy check
/// genuinely requires a fresh factor — then contrasts a SAML session that
/// has FACTOR_IDP_MFA against one that only has FACTOR_SAML_RESPONSE.
///
/// OrgPolicyRequiredAuthoritiesRepository checks user_verification.verified_at
/// freshness, not the live Authentication's own authorities — so the "with
/// FACTOR_IDP_MFA" case must actually publish a real AuthenticationSuccessEvent
/// first (same as a genuine login would), letting LoginRecordingListener do
/// the verified_at write, before hitting /oauth2/authorize. Injecting the
/// Authentication directly via .with(authentication(...)) without that step
/// would skip the exact mechanism this test means to prove.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 10: FACTOR_IDP_MFA satisfies an org's own MFA-interval policy")
class Phase10IdpMfaSatisfiesOrgPolicyTests {

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
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("SAML + FACTOR_IDP_MFA satisfies a DAILY-mode org's policy without an OTT redirect")
    void samlWithIdpMfaSkipsOttPrompt() throws Exception {
        String username = seedDailyModeUser("phase10-idp-mfa@test");

        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY),
                FactorGrantedAuthority.fromAuthority(SamlUserAuthoritiesConverter.IDP_MFA_AUTHORITY));

        // Simulates the login itself completing — LoginRecordingListener
        // writes user_verification.verified_at=now because FACTOR_IDP_MFA is
        // present, exactly as a real SAML+MFA login would.
        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authenticationFor(username, authorities)));

        var result = mockMvc.perform(get(AUTHORIZE_REQUEST).with(authentication(authenticationFor(username, authorities))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
        if (result.getResponse().getStatus() >= 300 && result.getResponse().getStatus() < 400) {
            assertThat(result.getResponse().getRedirectedUrl())
                    .as("FACTOR_IDP_MFA must satisfy the org's own DAILY policy — no OTT step-up needed")
                    .doesNotContain("/ott/request");
        }
    }

    @Test
    @DisplayName("SAML without FACTOR_IDP_MFA still needs the OTT step-up for the SAME DAILY-mode org")
    void samlWithoutIdpMfaStillNeedsOtt() throws Exception {
        String username = seedDailyModeUser("phase10-no-idp-mfa@test");

        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY));
        var result = mockMvc.perform(get(AUTHORIZE_REQUEST).with(authentication(authenticationFor(username, authorities))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isIn(301, 302, 303, 307, 308);
        assertThat(result.getResponse().getRedirectedUrl())
                .as("without an IdP-asserted MFA factor, a SAML session must be routed into the same OTT step-up a password session would get")
                .contains("/ott/request");
    }

    private String seedDailyModeUser(String username) {
        var user = userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash("n/a")
                .enabled(true)
                .failedAttempts(0)
                .build());
        var org = organizationRepository.save(Organization.builder()
                .code(username).name(username).active(true).build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId()).mfaMode(MfaMode.DAILY).ipRestrictionEnabled(false).build());
        membershipRepository.save(Membership.builder().user(user).organization(org).active(true).build());
        // Deliberately no user_verification row at all: FACTOR_OTT-equivalent
        // freshness is required and unmet, same setup Phase4Chain1MissingFactorTests uses.
        return username;
    }

    private static TestingAuthenticationToken authenticationFor(String username, List<GrantedAuthority> authorities) {
        var authentication = new TestingAuthenticationToken(username, "n/a", authorities);
        authentication.setAuthenticated(true);
        return authentication;
    }
}
