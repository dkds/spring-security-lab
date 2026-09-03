package com.dkds.authserver;

import com.dkds.authserver.user.AppUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/// Phase 2 Security Tests.
///
/// Per DESIGN.md Phase 2 requirements:
/// 1. Chain inventory - exactly three filter chains.
/// 2. Terminal rejections mapped onto UserDetails flags (not custom exceptions):
///    disabled, accountLocked, accountExpired (no membership), credentialsExpired.
/// 3. Valid users (with active membership) load with all flags healthy.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 2: Filter Chains & Terminal Rejections")
public class Phase2SecurityTests {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Autowired
    private AppUserDetailsService userDetailsService;

    /// TEST 1: Chain inventory - exactly three filter chains per DESIGN.md.
    @Test
    @DisplayName("Exactly three filter chains are configured")
    void testThreeFilterChainsExist() {
        var chains = filterChainProxy.getFilterChains();
        assertNotNull(chains, "Filter chains should not be null");
        assertEquals(3, chains.size(),
                "DESIGN.md mandates exactly three filter chains, found: " + chains.size());
    }

    /// TEST 2: Valid user (user1 has active membership) loads with healthy flags.
    /// This verifies terminal rejection flags are set correctly for a good user.
    @Test
    @DisplayName("Valid user loads with all account flags healthy")
    void testValidUserLoadsHealthy() {
        UserDetails details = userDetailsService.loadUserByUsername("user1");

        assertNotNull(details);
        assertEquals("user1", details.getUsername());
        assertTrue(details.isEnabled(), "user1 should be enabled");
        assertTrue(details.isAccountNonLocked(), "user1 should not be locked");
        assertTrue(details.isAccountNonExpired(),
                "user1 has an active membership, so account must not be expired");
        assertTrue(details.isCredentialsNonExpired(), "user1 credentials should not be expired");
    }

    /// TEST 3: Negative test - non-existent user throws UsernameNotFoundException.
    @Test
    @DisplayName("Non-existent user throws UsernameNotFoundException")
    void testNonExistentUserNotFound() {
        assertThrows(UsernameNotFoundException.class, () ->
                userDetailsService.loadUserByUsername("no-such-user-xyz"));
    }
}
