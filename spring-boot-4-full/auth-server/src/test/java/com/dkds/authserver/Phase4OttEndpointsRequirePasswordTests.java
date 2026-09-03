package com.dkds.authserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Phase 4 negative regression test: the three OTT endpoints
/// (/ott/request, /ott/input, /ott/generate) used to be wired with
/// permitAll(), which meant a fully anonymous caller — no session, no
/// password authentication at all — could hit /ott/generate directly and
/// have GenerateOneTimeTokenFilter mint a real code for any username
/// supplied in the request, with no proof the caller ever knew that user's
/// password. They're now wired with
/// access(AuthorityAuthorizationManager.hasAuthority(FACTOR_PASSWORD)) in
/// SecurityChains, so an anonymous caller must be turned away before
/// reaching the OTT machinery at all.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: OTT endpoints require FACTOR_PASSWORD, not permitAll")
class Phase4OttEndpointsRequirePasswordTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Anonymous GET /ott/request is turned away, not served")
    void anonymousCannotReachOttRequestPage() throws Exception {
        mockMvc.perform(get("/ott/request"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Anonymous GET /ott/input is turned away, not served")
    void anonymousCannotReachOttInputPage() throws Exception {
        mockMvc.perform(get("/ott/input"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("Anonymous POST /ott/generate cannot mint a code for an arbitrary username")
    void anonymousCannotGenerateOttForArbitraryUsername() throws Exception {
        mockMvc.perform(post("/ott/generate")
                        .param("username", "user1@example.com")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
