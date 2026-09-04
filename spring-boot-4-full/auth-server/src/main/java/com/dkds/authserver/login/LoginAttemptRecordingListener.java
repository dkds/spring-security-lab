package com.dkds.authserver.login;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/// Records every password-login attempt (success and failure) into
/// login_attempt — nothing did this before Phase 9; CaptchaService is the
/// first consumer.
///
/// Deliberately scoped to form login ONLY, unlike LoginRecordingListener's
/// own "one point regardless of mechanism" last_login_at/verified_at
/// recording: this is a DIFFERENT concern (password-guessing brute-force
/// signal), and CaptchaFilter itself only ever gates the password path
/// (DESIGN.md: "inside FormLoginConfigurer only") — recording SAML/OTT
/// outcomes here would just dilute the failure count CaptchaService actually
/// needs. Detected by the Authentication token's own type
/// (UsernamePasswordAuthenticationToken), which is reliable for both success
/// and failure events: a failed attempt hasn't been granted any authorities
/// yet to check instead, so the token TYPE — not authorities — is the one
/// signal available uniformly on both event kinds.
@Component
@RequiredArgsConstructor
public class LoginAttemptRecordingListener {

    private final LoginAttemptRepository loginAttemptRepository;
    private final Clock clock;

    @EventListener
    @Transactional
    public void onSuccess(AuthenticationSuccessEvent event) {
        record(event.getAuthentication(), true);
    }

    @EventListener
    @Transactional
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        record(event.getAuthentication(), false);
    }

    private void record(Authentication authentication, boolean success) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken)) {
            return;
        }
        String ipAddress = (authentication.getDetails() instanceof WebAuthenticationDetails details)
                ? details.getRemoteAddress()
                : "unknown";

        loginAttemptRepository.save(LoginAttempt.builder()
                .username(authentication.getName())
                .ipAddress(ipAddress)
                .success(success)
                .attemptedAt(clock.instant())
                .build());
    }
}
