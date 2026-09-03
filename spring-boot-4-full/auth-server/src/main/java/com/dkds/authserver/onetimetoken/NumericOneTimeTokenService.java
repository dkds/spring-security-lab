package com.dkds.authserver.onetimetoken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ott.DefaultOneTimeToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;

/// JPA-backed {@link OneTimeTokenService} that generates six-digit numeric codes.
///
/// Per DESIGN.md:
/// - Six-digit numeric code, NOT a magic link.
/// - Storage via JPA (one_time_token table).
/// - 5-minute TTL enforced on every generate().
/// - Single-use: token is deleted on consume().
/// - No otp_challenge table.
@Service
@Slf4j
@RequiredArgsConstructor
public class NumericOneTimeTokenService implements OneTimeTokenService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeTokenRepository repository;
    private final Clock clock;

    @Override
    @Transactional
    public org.springframework.security.authentication.ott.OneTimeToken generate(GenerateOneTimeTokenRequest request) {
        var code = generateNumericCode();
        var expiresAt = clock.instant().plus(TTL);

        // At most one outstanding code per user: a fresh request always
        // supersedes whatever code (if any) is still live for them.
        repository.deleteByUsername(request.getUsername());
        // token_value is the PK, so also guard against the rare case where
        // the freshly generated code collides with someone else's live one.
        repository.findById(code).ifPresent(t -> repository.deleteById(t.getTokenValue()));

        var entity = OneTimeToken.builder()
                .tokenValue(code)
                .username(request.getUsername())
                .expiresAt(expiresAt)
                .build();
        repository.save(entity);

        log.debug("Generated OTT for user={} expiresAt={}", request.getUsername(), expiresAt);
        return new DefaultOneTimeToken(code, request.getUsername(), expiresAt);
    }

    @Override
    @Transactional
    public org.springframework.security.authentication.ott.OneTimeToken consume(OneTimeTokenAuthenticationToken authenticationToken) {
        var tokenValue = authenticationToken.getTokenValue();
        var now = clock.instant();

        return repository.findByTokenValueAndExpiresAtAfter(tokenValue, now)
                .map(entity -> {
                    repository.deleteById(entity.getTokenValue());
                    log.debug("Consumed OTT for user={}", entity.getUsername());
                    return (org.springframework.security.authentication.ott.OneTimeToken) new DefaultOneTimeToken(
                            entity.getTokenValue(),
                            entity.getUsername(),
                            entity.getExpiresAt());
                })
                .orElseGet(() -> {
                    log.debug("OTT consume failed: token not found or expired");
                    return null;
                });
    }

    /// Returns a cryptographically secure six-digit numeric code.
    public static String generateNumericCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
