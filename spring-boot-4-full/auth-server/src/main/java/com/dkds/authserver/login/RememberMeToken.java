package com.dkds.authserver.login;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/// Backs JpaPersistentTokenRepository — one row per active "remember me"
/// series/token pair (Spring Security's persistent-token cookie scheme:
/// PersistentTokenBasedRememberMeServices, not the simpler hash-based
/// TokenBasedRememberMeServices). series is an application-assigned @Id
/// (a random value Spring Security generates per login), but unlike
/// SeenSamlAssertion this entity is genuinely read/updated afterward — every
/// remember-me-authenticated request rotates tokenValue for its series — so
/// it does NOT implement Persistable to force persist(): plain save() is
/// exactly the create-or-update behavior this needs.
@Entity
@Table(name = "remember_me_token")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RememberMeToken {

    @Id
    @Column(name = "series")
    private String series;

    @Column(nullable = false)
    private String username;

    @Column(name = "token_value", nullable = false)
    private String tokenValue;

    @Column(name = "last_used", nullable = false)
    private Instant lastUsed;
}
