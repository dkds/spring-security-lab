package com.dkds.resourceserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 6 regression test, per PLAN.md test 3: "common-security publishes
/// no Customizer<HttpSecurity> bean carrying a mechanism."
///
/// Spring auto-applies EVERY Customizer<HttpSecurity> bean in the context
/// ambiently to EVERY SecurityFilterChain being built — the exact footgun
/// already documented on auth-server's FormLoginConfigurer/OneTimeTokenConfigurer
/// (both plain AbstractHttpConfigurer classes, applied explicitly via
/// http.with(...), never as Customizer<HttpSecurity> beans, for this same
/// reason). common-security's ResourceServerSecurityConfig instead publishes
/// a complete SecurityFilterChain bean directly — this test is what actually
/// enforces that choice wasn't quietly reverted.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 6: common-security publishes no ambient Customizer<HttpSecurity> bean")
class Phase6NoAmbientCustomizerBeanTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("No bean of type Customizer<HttpSecurity> exists anywhere in this context")
    void noCustomizerHttpSecurityBeanExists() {
        var type = ResolvableType.forClassWithGenerics(Customizer.class, HttpSecurity.class);
        var names = context.getBeanNamesForType(type);
        assertThat(names).isEmpty();
    }
}
