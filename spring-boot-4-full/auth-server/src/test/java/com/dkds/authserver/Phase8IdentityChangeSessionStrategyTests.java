package com.dkds.authserver;

import com.dkds.authserver.security.IdentityChangeAwareSessionStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.savedrequest.RequestCache;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/// Phase 8, PLAN.md test 2: "Saved request cleared when the principal
/// changes, preserved when it does not."
///
/// IdentityChangeAwareSessionStrategy has existed since Phase 4/5, but per
/// AGENTS.md's own Known Gap #2 was "fully wired but only exercised
/// manually" — never actually unit-tested. This closes that gap directly,
/// not just for SAML: the class itself has no SAML dependency at all (it's
/// wired chain-wide via the shared SessionAuthenticationStrategy object —
/// see Saml2Configurer/FormLoginConfigurer), so testing it here covers every
/// mechanism on Chain 3, SAML included.
@DisplayName("Phase 8: IdentityChangeAwareSessionStrategy")
class Phase8IdentityChangeSessionStrategyTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Saved request is cleared when the incoming principal differs from the current one")
    void clearsSavedRequestOnIdentityChange() {
        RequestCache requestCache = mock(RequestCache.class);
        var strategy = new IdentityChangeAwareSessionStrategy(requestCache);

        setCurrentAuthentication("user-a");
        var incoming = authenticationFor("user-b");

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        strategy.onAuthentication(incoming, request, response);

        verify(requestCache).removeRequest(request, response);
    }

    @Test
    @DisplayName("Saved request is preserved when the incoming principal is the same as the current one")
    void preservesSavedRequestWhenIdentityUnchanged() {
        RequestCache requestCache = mock(RequestCache.class);
        var strategy = new IdentityChangeAwareSessionStrategy(requestCache);

        setCurrentAuthentication("user-a");
        var incoming = authenticationFor("user-a");

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        strategy.onAuthentication(incoming, request, response);

        verify(requestCache, never()).removeRequest(any(), any());
    }

    @Test
    @DisplayName("No prior authentication in the session (first login) does not clear anything")
    void noExistingAuthenticationDoesNotClear() {
        RequestCache requestCache = mock(RequestCache.class);
        var strategy = new IdentityChangeAwareSessionStrategy(requestCache);

        var incoming = authenticationFor("user-a");

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        strategy.onAuthentication(incoming, request, response);

        verify(requestCache, never()).removeRequest(any(), any());
    }

    private static void setCurrentAuthentication(String username) {
        SecurityContextHolder.getContext().setAuthentication(authenticationFor(username));
    }

    private static TestingAuthenticationToken authenticationFor(String username) {
        var authentication = new TestingAuthenticationToken(username, "n/a", List.of());
        authentication.setAuthenticated(true);
        return authentication;
    }
}
