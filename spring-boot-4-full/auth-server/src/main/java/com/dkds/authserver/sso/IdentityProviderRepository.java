package com.dkds.authserver.sso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface IdentityProviderRepository extends JpaRepository<IdentityProvider, Long> {
    List<IdentityProvider> findByActiveTrue();

    /// Used by SsoDiscoveryController to find which of a user's active
    /// memberships have SSO configured.
    List<IdentityProvider> findByOrgIdInAndActiveTrue(Collection<Long> orgIds);
}
