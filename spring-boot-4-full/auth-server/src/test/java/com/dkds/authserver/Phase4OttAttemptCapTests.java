package com.dkds.authserver;

import com.dkds.authserver.onetimetoken.EmailOttDeliveryHandler;
import com.dkds.authserver.onetimetoken.OneTimeTokenRepository;
import com.dkds.authserver.onetimetoken.OttAuthenticationFailureHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// Phase 4: per-code attempt cap on OTT submission.
///
/// `OneTimeTokenService` has no access to the HTTP session, so the cap is
/// enforced at the filter layer by `OttAuthenticationFailureHandler`, keyed
/// off the pending-username session attribute `EmailOttDeliveryHandler`
/// stamps when a code is issued. Tested directly against the handler rather
/// than through the full filter chain, consistent with how this project
/// tests OTT mechanics elsewhere (see Phase3OttTests).
@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 4: OTT attempt cap")
class Phase4OttAttemptCapTests {

    private static final String USERNAME = "victim@example.com";
    private static final AuthenticationException FAILURE = new BadCredentialsException("bad code");

    @Mock
    private OneTimeTokenRepository oneTimeTokenRepository;

    private OttAuthenticationFailureHandler handler;
    private MockHttpSession session;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new OttAuthenticationFailureHandler(oneTimeTokenRepository);
        session = new MockHttpSession();
        session.setAttribute(EmailOttDeliveryHandler.PENDING_USERNAME_SESSION_KEY, USERNAME);
        request = new MockHttpServletRequest();
        request.setSession(session);
    }

    private void fail() throws Exception {
        handler.onAuthenticationFailure(request, new MockHttpServletResponse(), FAILURE);
    }

    @Test
    @DisplayName("Below the cap: no invalidation, attempt count tracked in session")
    void belowCapDoesNotInvalidate() throws Exception {
        for (int i = 0; i < OttAuthenticationFailureHandler.ATTEMPT_CAP - 1; i++) {
            fail();
        }

        verify(oneTimeTokenRepository, never()).deleteByUsername(USERNAME);
        assertThat(session.getAttribute(OttAuthenticationFailureHandler.FAILED_ATTEMPTS_SESSION_KEY))
                .isEqualTo(OttAuthenticationFailureHandler.ATTEMPT_CAP - 1);
    }

    @Test
    @DisplayName("At the cap: the outstanding code is invalidated exactly once")
    void reachingCapInvalidatesOutstandingCode() throws Exception {
        for (int i = 0; i < OttAuthenticationFailureHandler.ATTEMPT_CAP; i++) {
            fail();
        }

        verify(oneTimeTokenRepository, times(1)).deleteByUsername(USERNAME);
        assertThat(session.getAttribute(OttAuthenticationFailureHandler.FAILED_ATTEMPTS_SESSION_KEY)).isNull();
        assertThat(session.getAttribute(EmailOttDeliveryHandler.PENDING_USERNAME_SESSION_KEY)).isNull();
    }

    @Test
    @DisplayName("Past the cap: further failures do not re-invalidate (nothing left to invalidate)")
    void furtherFailuresPastCapDoNotRepeat() throws Exception {
        for (int i = 0; i < OttAuthenticationFailureHandler.ATTEMPT_CAP + 2; i++) {
            fail();
        }

        verify(oneTimeTokenRepository, times(1)).deleteByUsername(USERNAME);
    }
}
