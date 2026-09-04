package com.dkds.authserver.login;

import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/// Decides whether a login attempt needs a captcha challenge, verifies one
/// when presented, and escalates repeated captcha failures into a lockout.
///
/// isRequired(...) consults recent login_attempt rows for this username+IP:
/// CREDENTIAL_FAILURE_THRESHOLD or more failures within LOOKBACK triggers a
/// challenge for the NEXT attempt. Scoped to username+IP together (not
/// username alone) so one address hammering many different usernames, and
/// one username being hammered from many addresses, both still accumulate
/// their own signal.
///
/// verify(...) is a deliberate lab/POC stand-in, not a real anti-bot check —
/// this codebase has no third-party captcha provider configured anywhere (no
/// API keys, no HTTP client for reCAPTCHA/hCaptcha/Turnstile). It exists to
/// prove the architectural seam (CaptchaFilter gates the real authentication
/// attempt behind this check when required) rather than to actually stop a
/// bot. Swap this method for a real provider call before this stops being a
/// lab.
///
/// recordCaptchaFailure(...) reuses AppUser.failedAttempts/lockedUntil —
/// fields that already existed for exactly this purpose (see AppUser.isLocked()/
/// clearLock(), and AppUserDetailsService's own .accountLocked(user.isLocked())
/// check) but were never actually written anywhere before this. CAPTCHA_FAILURE_THRESHOLD
/// wrong captcha submissions within the captcha-required window locks the
/// account for LOCKOUT_DURATION — enforced automatically by
/// DaoAuthenticationProvider on the very next attempt, correct password and
/// valid captcha included, with no separate lock-check needed in CaptchaFilter
/// itself (it adds one anyway, purely for a clearer message — see its own
/// comment).
@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaService {

    private static final int CREDENTIAL_FAILURE_THRESHOLD = 3;
    private static final Duration LOOKBACK = Duration.ofMinutes(15);

    private static final int CAPTCHA_FAILURE_THRESHOLD = 3;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final LoginAttemptRepository loginAttemptRepository;
    private final UserRepository userRepository;
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
        return recentFailures >= CREDENTIAL_FAILURE_THRESHOLD;
    }

    /// "wrong" is a reserved sentinel a caller can submit to deliberately
    /// simulate a failed verification — needed because CaptchaFilter already
    /// redirects on a null/blank token BEFORE calling this method (that's the
    /// separate "missing token" bounce, not a wrong answer), so without a
    /// dedicated wrong-answer value this method could never actually return
    /// false and recordCaptchaFailure(...) would be unreachable. Every other
    /// non-blank value still passes, matching the "type anything" lab UX.
    public boolean verify(String captchaToken) {
        return captchaToken != null && !captchaToken.isBlank() && !"wrong".equals(captchaToken);
    }

    /// Whether username is currently locked out (AppUser.lockedUntil in the
    /// future). CaptchaFilter uses this purely for a clearer, specific
    /// message before even attempting authentication — AppUserDetailsService
    /// enforces the actual lock independently either way, so this check is a
    /// UX nicety, not the source of truth.
    public boolean isLocked(String username) {
        return userRepository.findByUsername(username).map(AppUser::isLocked).orElse(false);
    }

    /// Records one wrong captcha submission for username, locking the
    /// account once CAPTCHA_FAILURE_THRESHOLD is reached within the current
    /// window. Only call this for a submission that was actually attempted
    /// and failed verify(...) — not for the informational bounce a caller
    /// gets on their first visit to the captcha-required state, before
    /// they've even seen the challenge (see CaptchaFilter).
    public void recordCaptchaFailure(String username) {
        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            // Same fail-open-on-unknown-username posture as the rest of the
            // login path (DESIGN.md's own anti-enumeration reasoning
            // elsewhere) — nothing to lock, and no need to say so.
            return;
        }
        int attempts = user.getFailedAttempts() + 1;
        if (attempts >= CAPTCHA_FAILURE_THRESHOLD) {
            user.setLockedUntil(clock.instant().plus(LOCKOUT_DURATION));
            user.setFailedAttempts(0);
            log.warn("Account '{}' locked for {} after {} wrong captcha submissions", username, LOCKOUT_DURATION, attempts);
        } else {
            user.setFailedAttempts(attempts);
        }
        userRepository.save(user);
    }
}
