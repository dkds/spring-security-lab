package com.dkds.authserver.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgSecurityPolicyRepository extends JpaRepository<OrgSecurityPolicy, Long> {
}
