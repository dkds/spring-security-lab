package com.dkds.authserver.organization;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "org_security_policy")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgSecurityPolicy {
    @Id
    @Column(name = "org_id")
    private Long orgId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MfaMode mfaMode;

    @Column(nullable = false)
    private Boolean ipRestrictionEnabled;
}
