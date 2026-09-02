package com.dkds.authserver.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrgIpRangeRepository extends JpaRepository<OrgIpRange, Long> {
    List<OrgIpRange> findByOrganizationId(Long organizationId);
}
