package com.dkds.resourceserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 6 regression test, per PLAN.md test 1: "chain inventory on
/// resource-server: resource-server filters only, no form login."
/// resource-server's entire security posture comes from the shared
/// common-security module (ResourceServerSecurityConfig, imported by
/// ResourceServerApplication) — this proves that produced exactly one
/// bearer-token-only chain, not auth-server's three-chain, form-login-capable
/// shape.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 6: resource-server chain inventory")
class Phase6ChainInventoryTests {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    @DisplayName("Exactly one filter chain — resource-server has no other security concern")
    void exactlyOneFilterChain() {
        assertThat(filterChainProxy.getFilterChains()).hasSize(1);
    }

    @Test
    @DisplayName("No form-login filter anywhere in that chain — bearer-token only")
    void noFormLoginFilter() {
        var filters = filterChainProxy.getFilterChains().get(0).getFilters();
        assertThat(filters).noneMatch(f -> f instanceof UsernamePasswordAuthenticationFilter);
    }
}
