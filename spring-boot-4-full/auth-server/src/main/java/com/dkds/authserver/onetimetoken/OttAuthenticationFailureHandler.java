package com.dkds.authserver.onetimetoken;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import java.io.IOException;

/// Caps repeated wrong-code submissions per DESIGN.md's "per-code attempt cap
/// of 5". The {@link OneTimeTokenService} interface has no access to the
/// HTTP session, so tracking has to live here at the filter layer instead:
/// {@link EmailOttDeliveryHandler} stamps the pending username and resets the
/// counter when a code is issued; this handler increments it on every failed
/// submission and, once the cap is hit, deletes the user's outstanding code
/// so it can no longer be entered correctly either — forcing a fresh
/// `/ott/generate` request.
@Slf4j
@RequiredArgsConstructor
public class OttAuthenticationFailureHandler implements AuthenticationFailureHandler {

    public static final int ATTEMPT_CAP = 5;
    public static final String FAILED_ATTEMPTS_SESSION_KEY = "ott.failedAttempts";

    private final OneTimeTokenRepository oneTimeTokenRepository;

    private final AuthenticationFailureHandler retryHandler =
            new SimpleUrlAuthenticationFailureHandler(EmailOttDeliveryHandler.OTT_INPUT_URL + "?error");
    private final AuthenticationFailureHandler cappedHandler =
            new SimpleUrlAuthenticationFailureHandler(EmailOttDeliveryHandler.OTT_INPUT_URL + "?error=cap");

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        var session = request.getSession(false);
        int attempts = 0;
        if (session != null) {
            var current = (Integer) session.getAttribute(FAILED_ATTEMPTS_SESSION_KEY);
            attempts = (current != null ? current : 0) + 1;
            session.setAttribute(FAILED_ATTEMPTS_SESSION_KEY, attempts);
        }

        if (attempts < ATTEMPT_CAP) {
            retryHandler.onAuthenticationFailure(request, response, exception);
            return;
        }

        var pendingUsername = (session != null)
                ? (String) session.getAttribute(EmailOttDeliveryHandler.PENDING_USERNAME_SESSION_KEY)
                : null;
        if (pendingUsername != null) {
            oneTimeTokenRepository.deleteByUsername(pendingUsername);
            log.debug("OTT attempt cap reached for '{}'; outstanding code invalidated", pendingUsername);
        }
        if (session != null) {
            session.removeAttribute(FAILED_ATTEMPTS_SESSION_KEY);
            session.removeAttribute(EmailOttDeliveryHandler.PENDING_USERNAME_SESSION_KEY);
        }
        cappedHandler.onAuthenticationFailure(request, response, exception);
    }
}
