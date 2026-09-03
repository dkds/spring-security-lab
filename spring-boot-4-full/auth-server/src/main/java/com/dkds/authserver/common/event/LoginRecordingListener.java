package com.dkds.authserver.common.event;

import com.dkds.authserver.authorization.UserVerification;
import com.dkds.authserver.authorization.UserVerificationRepository;
import com.dkds.authserver.onetimetoken.OneTimeTokenRepository;
import com.dkds.authserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/// Records login activity at a single point, per DESIGN.md: writes
/// `last_login_at` on every successful authentication, and
/// `user_verification.verified_at` only when that same authentication
/// carries FACTOR_OTT — never in a per-mechanism success handler, since a
/// write attached to form login would silently stop happening on the SSO
/// path.
///
/// Also invalidates any outstanding (unconsumed) OTT code for the user on
/// every successful login, so a previously issued but never-entered code
/// can't be replayed later.
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginRecordingListener {

    private final UserRepository userRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final Clock clock;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        var authentication = event.getAuthentication();
        var username = authentication.getName();
        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            log.warn("AuthenticationSuccessEvent for unknown username '{}'", username);
            return;
        }

        var now = clock.instant();
        user.setLastLoginAt(now);
        userRepository.save(user);

        boolean ottCompleted = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority instanceof FactorGrantedAuthority factor
                        && FactorGrantedAuthority.OTT_AUTHORITY.equals(factor.getAuthority()));
        if (ottCompleted) {
            recordOttVerification(user.getId(), now);
        }

        oneTimeTokenRepository.deleteByUsername(username);
    }

    private void recordOttVerification(Long userId, Instant now) {
        var verification = userVerificationRepository
                .findByUserIdAndMethod(userId, UserVerification.METHOD_EMAIL)
                .orElseGet(() -> UserVerification.builder()
                        .user(userRepository.getReferenceById(userId))
                        .method(UserVerification.METHOD_EMAIL)
                        .build());
        verification.setVerifiedAt(now);
        userVerificationRepository.save(verification);
    }
}
