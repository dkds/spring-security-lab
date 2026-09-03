package com.dkds.authserver.onetimetoken;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, String> {

    Optional<OneTimeToken> findByTokenValueAndExpiresAtAfter(String tokenValue, Instant now);

    @Modifying
    @Transactional
    @Query("DELETE FROM OneTimeToken t WHERE t.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
}
