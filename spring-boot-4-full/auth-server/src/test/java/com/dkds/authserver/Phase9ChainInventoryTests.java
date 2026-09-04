package com.dkds.authserver;

import com.dkds.authserver.login.CaptchaFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 9, PLAN.md's own test verbatim: "chain inventory confirms the
/// captcha filter is in the authentication chain and in no other."
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 9: CaptchaFilter chain inventory")
class Phase9ChainInventoryTests {

    @Autowired
    private FilterChainProxy filterChainProxy;

    @Test
    @DisplayName("CaptchaFilter is present in exactly one chain (the fallback chain), positioned before UsernamePasswordAuthenticationFilter")
    void captchaFilterIsOnlyOnFallbackChainBeforeUsernamePassword() {
        var chainsWithCaptchaFilter = filterChainProxy.getFilterChains().stream()
                .filter(chain -> chain.getFilters().stream().anyMatch(CaptchaFilter.class::isInstance))
                .toList();

        assertThat(chainsWithCaptchaFilter)
                .as("CaptchaFilter must be wired into exactly one chain (Chain 3), never Chain 1 or Chain 2")
                .hasSize(1);

        List<Filter> filters = chainsWithCaptchaFilter.get(0).getFilters();
        int captchaIndex = indexOfType(filters, CaptchaFilter.class);
        int usernamePasswordIndex = indexOfType(filters, UsernamePasswordAuthenticationFilter.class);

        assertThat(captchaIndex).isGreaterThanOrEqualTo(0);
        assertThat(usernamePasswordIndex).isGreaterThanOrEqualTo(0);
        assertThat(captchaIndex)
                .as("CaptchaFilter must run before UsernamePasswordAuthenticationFilter")
                .isLessThan(usernamePasswordIndex);
    }

    private static int indexOfType(List<Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
