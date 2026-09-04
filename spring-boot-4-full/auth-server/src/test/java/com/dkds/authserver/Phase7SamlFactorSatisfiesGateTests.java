package com.dkds.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.TestingAuthenticationToken;
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

/// Phase 7, PLAN.md test 1: "SAML login alone satisfies the gate; password
/// alone does not."
///
/// Exercises the REAL, fully wired AuthorizationManagerFactory bean
/// (SecurityChains Chain 1, /oauth2/authorize) — not a fresh hand-built
/// instance — same philosophy as Phase6ProfileEndpointTests. Uses a plain
/// TestingAuthenticationToken carrying FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY
/// rather than a real Saml2Authentication: SamlOrPasswordOttAuthorizationManager
/// only inspects Authentication#getAuthorities(), never the concrete
/// principal type, so this exercises exactly the code path that matters
/// without needing a live-signed assertion (that round trip was verified
/// manually against a real Keycloak instance instead — see AGENTS.md Known
/// Gaps for the same precedent on OTT's Mailpit-backed flow).
///
/// "password alone does not [satisfy the gate]" for an OTT-requiring org is
/// already covered by Phase4Chain1MissingFactorTests, which stays green
/// unchanged — SamlOrPasswordOttAuthorizationManager returns that policy's
/// own denial verbatim when no SAML factor is present, deliberately (see its
/// own javadoc). This test only adds the SAML-side half of the composition.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 7: SAML factor alone satisfies the /oauth2/authorize gate")
class Phase7SamlFactorSatisfiesGateTests {

    private static final String AUTHORIZE_REQUEST =
            "/oauth2/authorize?client_id=spa-client"
                    + "&redirect_uri=http://localhost:5173/callback"
                    + "&response_type=code"
                    + "&scope=openid"
                    + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                    + "&code_challenge_method=S256";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A principal authenticated with only FACTOR_SAML_RESPONSE (no password, no OTT) is not blocked by the factor gate")
    void samlFactorAloneIsNotBlockedByFactorGate() throws Exception {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY));
        var samlAuthentication = new TestingAuthenticationToken("ssouser@dkds.com", "n/a", authorities);
        samlAuthentication.setAuthenticated(true);

        var result = mockMvc.perform(get(AUTHORIZE_REQUEST).with(authentication(samlAuthentication)))
                .andReturn();

        int status = result.getResponse().getStatus();
        assertThat(status).as("SAML-authenticated principal must not be denied outright").isNotEqualTo(403);
        if (status >= 300 && status < 400) {
            String location = result.getResponse().getRedirectedUrl();
            assertThat(location)
                    .as("must not be routed into the OTT step-up flow or back to the password form — "
                            + "FACTOR_SAML_RESPONSE alone satisfies the factor gate")
                    .doesNotContain("/ott/request")
                    .doesNotContain("/login");
        }
    }
}
