package com.dkds.authserver.login;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RememberMeTokenRepository extends JpaRepository<RememberMeToken, String> {
    void deleteByUsername(String username);
}
