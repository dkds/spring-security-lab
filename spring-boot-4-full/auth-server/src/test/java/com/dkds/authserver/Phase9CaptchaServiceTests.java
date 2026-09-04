package com.dkds.authserver;

import com.dkds.authserver.login.CaptchaService;
import com.dkds.authserver.login.LoginAttempt;
import com.dkds.authserver.login.LoginAttemptRepository;
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
    @DisplayName("verify() rejects null/blank tokens and accepts a non-blank one")
    void verifyRejectsBlankAcceptsNonBlank() {
        assertThat(captchaService.verify(null)).isFalse();
        assertThat(captchaService.verify("")).isFalse();
        assertThat(captchaService.verify("   ")).isFalse();
        assertThat(captchaService.verify("some-token")).isTrue();
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
