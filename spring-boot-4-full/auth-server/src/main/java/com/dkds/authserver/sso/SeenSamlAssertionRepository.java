package com.dkds.authserver.sso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeenSamlAssertionRepository extends JpaRepository<SeenSamlAssertion, String> {
}
