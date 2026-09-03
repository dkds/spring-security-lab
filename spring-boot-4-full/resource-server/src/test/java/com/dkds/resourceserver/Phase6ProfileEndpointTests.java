package com.dkds.resourceserver;

import com.dkds.commonsecurity.RolesAndScopesJwtGrantedAuthoritiesConverter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Phase 6 regression test, standing in for PLAN.md test 2 ("end-to-end call
/// from portal-ui through to resource-server"). A real cross-process call
/// (live auth-server minting a real signed token, live resource-server
/// validating it over the network) is a manual verification step — same
/// precedent as Phase 3's Mailpit-backed OTT flow, see AGENTS.md Known Gaps.
///
/// What IS exercised automatically, and is the substantive thing "end-to-end"
/// is actually checking: the realistic access-token claim set auth-server's
/// AccessTokenCustomizer now writes (roles + the standard scope claim) is
/// correctly consumed by the ACTUAL JwtAuthenticationConverter bean from this
/// app's context — via ResourceServerSecurityConfig, in
/// common-security — proving both SCOPE_* and ROLE_* authorities reach a
/// real @RestController's authorization decision, not just a unit test of
/// the converter class in isolation.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Phase 6: /api/profile — realistic access-token claims")
class Phase6ProfileEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    @DisplayName("A valid bearer token with roles+scope claims maps to ROLE_* and SCOPE_* authorities")
    void validTokenMapsRolesAndScopeToAuthorities() throws Exception {
        mockMvc.perform(get("/api/profile")
                        .with(jwt()
                                .jwt(builder -> builder
                                        .claim("sub", "user1")
                                        .claim("scope", "api")
                                        .claim(RolesAndScopesJwtGrantedAuthoritiesConverter.ROLES_CLAIM_NAME,
                                                List.of("MEMBER")))
                                // Uses the REAL converter bean from context, not a
                                // fresh instance — this is what actually proves
                                // ResourceServerSecurityConfig wired it in.
                                .authorities(j -> jwtAuthenticationConverter.convert(j).getAuthorities())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user1"))
                .andExpect(jsonPath("$.roles[0]").value("MEMBER"))
                .andExpect(jsonPath("$.authorities", Matchers.hasItem("SCOPE_api")))
                .andExpect(jsonPath("$.authorities", Matchers.hasItem("ROLE_MEMBER")));
    }

    @Test
    @DisplayName("No bearer token at all is rejected")
    void noTokenRejected() throws Exception {
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized());
    }
}
