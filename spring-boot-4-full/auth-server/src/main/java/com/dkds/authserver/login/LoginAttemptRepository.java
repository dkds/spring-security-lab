package com.dkds.authserver.login;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    List<LoginAttempt> findByUsernameAndAttemptedAtAfter(String username, Instant since);

    List<LoginAttempt> findByUsernameAndIpAddressAndAttemptedAtAfter(String username, String ipAddress, Instant since);
}
