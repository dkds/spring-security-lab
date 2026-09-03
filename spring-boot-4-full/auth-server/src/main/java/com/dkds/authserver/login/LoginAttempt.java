package com.dkds.authserver.login;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/// Tracks login attempts for rate limiting and lockout purposes.
@Entity
@Table(name = "login_attempt")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private Boolean success;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;
}
