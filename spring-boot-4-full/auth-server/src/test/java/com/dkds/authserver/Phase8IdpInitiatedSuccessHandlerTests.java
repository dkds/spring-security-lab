package com.dkds.authserver;

import com.dkds.authserver.sso.IdpInitiatedSuccessHandler;
import com.dkds.authserver.sso.RelayStateValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// Per DESIGN.md: "IdP-initiated is the primary path. On an assertion with
/// no saved request, redirect to the SPA landing route... Validate RelayState
/// as untrusted input before redirecting."
@DisplayName("Phase 8: IdpInitiatedSuccessHandler")
class Phase8IdpInitiatedSuccessHandlerTests {

    private static final String SPA_LANDING_URI = "http://localhost:5173/";

    private final RelayStateValidator relayStateValidator = new RelayStateValidator(SPA_LANDING_URI);

    @Test
    @DisplayName("No saved request, no RelayState -> redirects to the fixed SPA landing URI")
    void noSavedRequestNoRelayStateRedirectsToLandingUri() throws Exception {
        var requestCache = mock(RequestCache.class);
        var handler = new IdpInitiatedSuccessHandler(requestCache, relayStateValidator, SPA_LANDING_URI);

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(requestCache.getRequest(request, response)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(SPA_LANDING_URI);
    }

    @Test
    @DisplayName("No saved request, a validated RelayState -> redirects to the RelayState target")
    void noSavedRequestValidRelayStateRedirectsThere() throws Exception {
        var requestCache = mock(RequestCache.class);
        var handler = new IdpInitiatedSuccessHandler(requestCache, relayStateValidator, SPA_LANDING_URI);

        var request = new MockHttpServletRequest();
        request.setParameter("RelayState", "http://localhost:5173/dashboard");
        var response = new MockHttpServletResponse();
        when(requestCache.getRequest(request, response)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:5173/dashboard");
    }

    @Test
    @DisplayName("No saved request, a foreign-host RelayState -> falls back to the fixed SPA landing URI, not the foreign host")
    void noSavedRequestForeignRelayStateFallsBackToLandingUri() throws Exception {
        var requestCache = mock(RequestCache.class);
        var handler = new IdpInitiatedSuccessHandler(requestCache, relayStateValidator, SPA_LANDING_URI);

        var request = new MockHttpServletRequest();
        request.setParameter("RelayState", "http://evil.example.com/phish");
        var response = new MockHttpServletResponse();
        when(requestCache.getRequest(request, response)).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo(SPA_LANDING_URI);
    }

    @Test
    @DisplayName("A saved request present -> resumes it instead of going to the SPA landing URI")
    void savedRequestPresentIsResumed() throws Exception {
        var requestCache = mock(RequestCache.class);
        var handler = new IdpInitiatedSuccessHandler(requestCache, relayStateValidator, SPA_LANDING_URI);

        var savedRequest = mock(SavedRequest.class);
        when(savedRequest.getRedirectUrl()).thenReturn("http://localhost:9000/oauth2/authorize?client_id=spa-client");

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        when(requestCache.getRequest(request, response)).thenReturn(savedRequest);

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:9000/oauth2/authorize?client_id=spa-client");
    }

    private static TestingAuthenticationToken authentication() {
        var authentication = new TestingAuthenticationToken("ssouser@dkds.com", "n/a", List.of());
        authentication.setAuthenticated(true);
        return authentication;
    }
}
