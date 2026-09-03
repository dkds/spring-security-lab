package com.dkds.authserver.organization;

import com.dkds.authserver.user.AppUser;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "membership", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "org_id"})
})
@IdClass(MembershipId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Membership {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private Boolean active;
}
