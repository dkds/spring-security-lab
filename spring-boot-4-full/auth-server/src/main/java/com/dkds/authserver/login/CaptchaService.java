package com.dkds.authserver.login;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/// Decides whether a login attempt needs a captcha challenge, and verifies
/// one when presented.
///
/// isRequired(...) consults recent login_attempt rows for this username+IP:
/// FAILURE_THRESHOLD or more failures within LOOKBACK triggers a challenge
/// for the NEXT attempt. Scoped to username+IP together (not username alone)
/// so one address hammering many different usernames, and one username being
/// hammered from many addresses, both still accumulate their own signal.
///
/// verify(...) is a deliberate lab/POC stand-in, not a real anti-bot check —
/// this codebase has no third-party captcha provider configured anywhere (no
/// API keys, no HTTP client for reCAPTCHA/hCaptcha/Turnstile). It exists to
/// prove the architectural seam (CaptchaFilter gates the real authentication
/// attempt behind this check when required) rather than to actually stop a
/// bot. Swap this method for a real provider call before this stops being a
/// lab.
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final int FAILURE_THRESHOLD = 3;
    private static final Duration LOOKBACK = Duration.ofMinutes(15);

    private final LoginAttemptRepository loginAttemptRepository;
    private final Clock clock;

    public boolean isRequired(String username, String ipAddress) {
        if (username == null || username.isBlank()) {
            return false;
        }
        var since = clock.instant().minus(LOOKBACK);
        long recentFailures = loginAttemptRepository
                .findByUsernameAndIpAddressAndAttemptedAtAfter(username, ipAddress, since)
                .stream()
                .filter(attempt -> !attempt.getSuccess())
                .count();
        return recentFailures >= FAILURE_THRESHOLD;
    }

    public boolean verify(String captchaToken) {
        return captchaToken != null && !captchaToken.isBlank();
    }
}
