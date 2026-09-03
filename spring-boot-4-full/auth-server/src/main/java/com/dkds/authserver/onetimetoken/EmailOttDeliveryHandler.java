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
