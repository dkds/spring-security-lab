package com.dkds.authserver.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, MembershipId> {
    List<Membership> findByUserIdAndActiveTrue(Long userId);
    
    List<Membership> findByOrganizationIdAndActiveTrue(Long organizationId);
}
