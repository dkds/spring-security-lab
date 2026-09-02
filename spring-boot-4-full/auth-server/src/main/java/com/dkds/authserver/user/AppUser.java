package com.dkds.authserver.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean enabled;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(nullable = false)
    private Integer failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        return Instant.now().isBefore(lockedUntil);
    }

    public void clearLock() {
        this.lockedUntil = null;
        this.failedAttempts = 0;
    }
}
