package com.dkds.resourceserver;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Regression test for the dummy /api/tasks demo endpoint, same shape as
/// Phase6ProfileEndpointTests: a valid bearer token reaches the controller,
/// no token at all is rejected by the real security chain.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("/api/tasks — dummy list endpoint")
class TasksEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A valid bearer token can fetch the task list")
    void validTokenReturnsTasks() throws Exception {
        mockMvc.perform(get("/api/tasks").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", Matchers.hasSize(4)))
                .andExpect(jsonPath("$[0].title").value("Review pull request"));
    }

    @Test
    @DisplayName("No bearer token at all is rejected")
    void noTokenRejected() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }
}
