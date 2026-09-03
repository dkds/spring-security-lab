package com.dkds.authserver.onetimetoken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/// JPA entity for OTT storage.
///
/// Per DESIGN.md: no otp_challenge table — this IS the OTT storage.
/// Table name uses singular convention consistent with other entities.
@Entity
@Table(name = "one_time_token")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneTimeToken {

    /// Six-digit numeric code — this is also the primary key.
    @Id
    @Column(name = "token_value", nullable = false, length = 6)
    private String tokenValue;

    @Column(nullable = false)
    private String username;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
