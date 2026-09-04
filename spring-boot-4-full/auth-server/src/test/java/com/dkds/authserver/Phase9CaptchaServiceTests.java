package com.dkds.authserver;

import com.dkds.authserver.login.CaptchaService;
import com.dkds.authserver.login.LoginAttempt;
import com.dkds.authserver.login.LoginAttemptRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 9: CaptchaService")
class Phase9CaptchaServiceTests {

    private static final String USERNAME = "phase9-captcha@test";
    private static final String IP = "203.0.113.10";

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Not required with no recent failures")
    void notRequiredWithNoFailures() {
        assertThat(captchaService.isRequired(USERNAME, IP)).isFalse();
    }

    @Test
    @DisplayName("Not required below the failure threshold")
    void notRequiredBelowThreshold() {
        recordFailures(2);
        assertThat(captchaService.isRequired(USERNAME, IP)).isFalse();
    }

    @Test
    @DisplayName("Required at the failure threshold")
    void requiredAtThreshold() {
        recordFailures(3);
        assertThat(captchaService.isRequired(USERNAME, IP)).isTrue();
    }

    @Test
    @DisplayName("A successful attempt does not count toward the failure threshold")
    void successfulAttemptsDoNotCount() {
        recordAttempt(true);
        recordAttempt(true);
        recordAttempt(true);
        assertThat(captchaService.isRequired(USERNAME, IP)).isFalse();
    }

    @Test
    @DisplayName("Failures from a different IP do not trigger a captcha for this IP")
    void failuresFromDifferentIpDoNotCount() {
        var other = LoginAttempt.builder()
                .username(USERNAME).ipAddress("198.51.100.20").success(false).attemptedAt(clock.instant()).build();
        loginAttemptRepository.save(other);
        loginAttemptRepository.save(other);
        loginAttemptRepository.save(other);

        assertThat(captchaService.isRequired(USERNAME, IP)).isFalse();
    }

    @Test
    @DisplayName("verify() rejects null/blank tokens and the reserved 'wrong' sentinel, accepts any other non-blank value")
    void verifyRejectsBlankAndWrongAcceptsOtherNonBlank() {
        assertThat(captchaService.verify(null)).isFalse();
        assertThat(captchaService.verify("")).isFalse();
        assertThat(captchaService.verify("   ")).isFalse();
        assertThat(captchaService.verify("wrong")).isFalse();
        assertThat(captchaService.verify("some-token")).isTrue();
    }

    @Test
    @DisplayName("isLocked() is false for a user with no lockedUntil")
    void isLockedFalseWhenNeverLocked() {
        var username = "phase9-lock-unset@test";
        userRepository.save(AppUser.builder()
                .username(username).passwordHash("n/a").enabled(true).failedAttempts(0).build());

        assertThat(captchaService.isLocked(username)).isFalse();
    }

    @Test
    @DisplayName("isLocked() is false for an unknown username")
    void isLockedFalseForUnknownUsername() {
        assertThat(captchaService.isLocked("phase9-does-not-exist@test")).isFalse();
    }

    @Test
    @DisplayName("recordCaptchaFailure() locks the account after CAPTCHA_FAILURE_THRESHOLD wrong submissions and resets the counter")
    void recordCaptchaFailureLocksAtThreshold() {
        var username = "phase9-captcha-lockout@test";
        userRepository.save(AppUser.builder()
                .username(username).passwordHash("n/a").enabled(true).failedAttempts(0).build());

        captchaService.recordCaptchaFailure(username);
        captchaService.recordCaptchaFailure(username);
        assertThat(captchaService.isLocked(username))
                .as("below the threshold, not yet locked")
                .isFalse();

        captchaService.recordCaptchaFailure(username);
        assertThat(captchaService.isLocked(username))
                .as("threshold reached, account is locked")
                .isTrue();

        var locked = userRepository.findByUsername(username).orElseThrow();
        assertThat(locked.getFailedAttempts())
                .as("the counter resets once it triggers a lock, rather than growing unbounded")
                .isZero();
    }

    @Test
    @DisplayName("recordCaptchaFailure() for an unknown username is a no-op, not an error")
    void recordCaptchaFailureUnknownUsernameIsNoOp() {
        captchaService.recordCaptchaFailure("phase9-captcha-unknown@test");
        // No exception, and nothing to assert against — the point is that
        // this must not throw for the same anti-enumeration reasons the rest
        // of the login path already follows.
    }

    private void recordFailures(int count) {
        for (int i = 0; i < count; i++) {
            recordAttempt(false);
        }
    }

    private void recordAttempt(boolean success) {
        loginAttemptRepository.save(LoginAttempt.builder()
                .username(USERNAME)
                .ipAddress(IP)
                .success(success)
                .attemptedAt(Instant.now(clock))
                .build());
    }
}
