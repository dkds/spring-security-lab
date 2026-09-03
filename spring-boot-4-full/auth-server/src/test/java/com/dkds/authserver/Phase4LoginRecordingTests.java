package com.dkds.authserver;

import com.dkds.authserver.authorization.UserVerification;
import com.dkds.authserver.authorization.UserVerificationRepository;
import com.dkds.authserver.onetimetoken.OneTimeToken;
import com.dkds.authserver.onetimetoken.OneTimeTokenRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 4: login recording, written at a single point via
/// `AuthenticationSuccessEvent` per DESIGN.md — `last_login_at` on every
/// success, `user_verification.verified_at` only when FACTOR_OTT was part of
/// that success, and any outstanding OTT code invalidated either way.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: Login recording")
class Phase4LoginRecordingTests {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserVerificationRepository userVerificationRepository;

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    private AppUser createUser(String username) {
        return userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash("{noop}unused")
                .enabled(true)
                .failedAttempts(0)
                .build());
    }

    @Test
    @DisplayName("Password-only success records last_login_at but not verified_at")
    void passwordOnlySuccessRecordsLastLoginOnly() {
        var user = createUser("phase4-login-password");
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY));
        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a", authorities);

        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

        var reloaded = userRepository.findByUsername("phase4-login-password").orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(userVerificationRepository.findByUserIdAndMethod(user.getId(), UserVerification.METHOD_EMAIL))
                .isEmpty();
    }

    @Test
    @DisplayName("Success including FACTOR_OTT records both last_login_at and verified_at, and clears outstanding codes")
    void ottSuccessRecordsVerificationAndClearsOutstandingCode() {
        var user = createUser("phase4-login-ott");
        oneTimeTokenRepository.save(OneTimeToken.builder()
                .tokenValue("135790")
                .username(user.getUsername())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_MEMBER"),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY),
                FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.OTT_AUTHORITY));
        var authentication = new UsernamePasswordAuthenticationToken(user.getUsername(), "n/a", authorities);

        eventPublisher.publishEvent(new AuthenticationSuccessEvent(authentication));

        var reloaded = userRepository.findByUsername("phase4-login-ott").orElseThrow();
        assertThat(reloaded.getLastLoginAt()).isNotNull();

        var verification = userVerificationRepository
                .findByUserIdAndMethod(user.getId(), UserVerification.METHOD_EMAIL)
                .orElseThrow();
        assertThat(verification.getVerifiedAt()).isNotNull();

        assertThat(oneTimeTokenRepository.findById("135790")).isEmpty();
    }
}
