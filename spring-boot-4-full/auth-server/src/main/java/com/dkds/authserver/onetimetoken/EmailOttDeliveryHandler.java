package com.dkds.authserver.onetimetoken;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.security.web.authentication.ott.RedirectOneTimeTokenGenerationSuccessHandler;

import java.io.IOException;

/// Delivers the OTT six-digit code via email (Mailpit in dev) and then
/// redirects the browser to the OTT entry screen.
///
/// Per DESIGN.md:
/// - Email delivery only. No SMS, no TOTP.
/// - Custom entry screen (showDefaultSubmitPage = false).
/// - Redirect to /ott/input after sending the email.
@Slf4j
@RequiredArgsConstructor
public class EmailOttDeliveryHandler implements OneTimeTokenGenerationSuccessHandler {

    public static final String OTT_INPUT_URL = "/ott/input";

    /// Landing page for a principal missing FACTOR_OTT: explains that a code
    /// is needed and lets them request one. Deliberately separate from
    /// SecurityConstants.LOGIN_PAGE — that page only knows how to render a
    /// username/password form, and a principal who lands here has already
    /// authenticated with a password; showing them that form again just
    /// re-submits the same credentials and loops back to the same denial.
    public static final String OTT_REQUEST_URL = "/ott/request";

    /// Session attribute naming which user the current OTT challenge is
    /// for, so {@link OttAuthenticationFailureHandler} knows whose
    /// outstanding code to invalidate once the attempt cap is hit.
    public static final String PENDING_USERNAME_SESSION_KEY = "ott.pendingUsername";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    /// Fallback delegate — redirects browser to the OTT input page.
    private final RedirectOneTimeTokenGenerationSuccessHandler redirect =
            new RedirectOneTimeTokenGenerationSuccessHandler(OTT_INPUT_URL);

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       OneTimeToken oneTimeToken) throws IOException {
        sendEmail(oneTimeToken);
        var session = request.getSession();
        session.setAttribute(PENDING_USERNAME_SESSION_KEY, oneTimeToken.getUsername());
        session.removeAttribute(OttAuthenticationFailureHandler.FAILED_ATTEMPTS_SESSION_KEY);
        redirect.handle(request, response, oneTimeToken);
    }

    private void sendEmail(OneTimeToken token) {
        try {
            var message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(token.getUsername()); // username is email for this app
            message.setSubject("Your verification code");
            message.setText("""
                    Your verification code is: %s
                    
                    This code expires in 5 minutes and can only be used once.
                    
                    If you did not request this code, please ignore this email.
                    """.formatted(token.getTokenValue()));
            mailSender.send(message);
            log.debug("OTT code sent to {}", token.getUsername());
        } catch (Exception ex) {
            // Log but don't expose delivery failure details to the browser.
            // The user will see a generic "check your email" message either way.
            log.error("Failed to send OTT email to {}", token.getUsername(), ex);
        }
    }
}
