package com.dkds.authserver.authorization;

import com.dkds.authserver.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_verification", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "method"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private String method;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
