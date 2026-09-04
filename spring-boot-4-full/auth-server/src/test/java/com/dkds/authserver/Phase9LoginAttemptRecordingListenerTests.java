package com.dkds.authserver;

import com.dkds.authserver.login.LoginAttemptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Covers the machinery CaptchaService actually depends on: every
/// password-login attempt (success and failure) lands in login_attempt, and
/// nothing else does — SAML/OTT outcomes are deliberately not recorded here
/// (see LoginAttemptRecordingListener's own javadoc for why).
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 9: LoginAttemptRecordingListener")
class Phase9LoginAttemptRecordingListenerTests {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Test
    @DisplayName("A successful password login is recorded with success=true and the caller's IP")
    void successfulPasswordLoginIsRecorded() {
        String username = "phase9-listener-success@test";
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.50");

        var authentication = new UsernamePasswordAuthenticationToken(username, "n/a", List.of());
        authentication.setDetails(new WebAuthenticationDetails(request));

        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

        var attempts = loginAttemptRepository.findByUsernameAndAttemptedAtAfter(username, Instant.now().minusSeconds(60));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getSuccess()).isTrue();
        assertThat(attempts.get(0).getIpAddress()).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("A failed password login is recorded with success=false")
    void failedPasswordLoginIsRecorded() {
        String username = "phase9-listener-failure@test";
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.51");

        var authentication = new UsernamePasswordAuthenticationToken(username, "wrong-password");
        authentication.setDetails(new WebAuthenticationDetails(request));

        eventPublisher.publishEvent(
                new AuthenticationFailureBadCredentialsEvent(authentication, new BadCredentialsException("bad")));

        var attempts = loginAttemptRepository.findByUsernameAndAttemptedAtAfter(username, Instant.now().minusSeconds(60));
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getSuccess()).isFalse();
    }

    @Test
    @DisplayName("A non-password authentication (e.g. SAML/OTT) is NOT recorded as a login_attempt")
    void nonPasswordAuthenticationIsNotRecorded() {
        String username = "phase9-listener-other-mechanism@test";
        var authentication = new TestingAuthenticationToken(username, "n/a", List.of());
        authentication.setAuthenticated(true);

        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

        var attempts = loginAttemptRepository.findByUsernameAndAttemptedAtAfter(username, Instant.now().minusSeconds(60));
        assertThat(attempts).isEmpty();
    }
}
