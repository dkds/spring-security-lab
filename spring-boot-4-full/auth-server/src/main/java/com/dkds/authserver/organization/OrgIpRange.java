package com.dkds.authserver.organization;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "org_ip_range")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgIpRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @Column(nullable = false)
    private String cidr;
}
