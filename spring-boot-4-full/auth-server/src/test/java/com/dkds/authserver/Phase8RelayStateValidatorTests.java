package com.dkds.authserver;

import com.dkds.authserver.sso.RelayStateValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 8, PLAN.md test 3: "RelayState pointing at a foreign host is
/// rejected."
@DisplayName("Phase 8: RelayStateValidator")
class Phase8RelayStateValidatorTests {

    private final RelayStateValidator validator = new RelayStateValidator("http://localhost:5173/");

    @Test
    @DisplayName("A RelayState on the SPA's own origin is accepted")
    void sameOriginIsAccepted() {
        assertThat(validator.validate("http://localhost:5173/dashboard")).isEqualTo("http://localhost:5173/dashboard");
    }

    @Test
    @DisplayName("A RelayState pointing at a foreign host is rejected")
    void foreignHostIsRejected() {
        assertThat(validator.validate("http://evil.example.com/phish")).isNull();
    }

    @Test
    @DisplayName("A RelayState on the same host but a different port is rejected")
    void differentPortIsRejected() {
        assertThat(validator.validate("http://localhost:9999/dashboard")).isNull();
    }

    @Test
    @DisplayName("A relative RelayState (no scheme/host) is rejected, not resolved against anything")
    void relativeValueIsRejected() {
        assertThat(validator.validate("/dashboard")).isNull();
    }

    @Test
    @DisplayName("A malformed RelayState is rejected rather than throwing")
    void malformedValueIsRejected() {
        assertThat(validator.validate("http://[invalid")).isNull();
    }

    @Test
    @DisplayName("Absent RelayState (null or blank) is rejected")
    void absentValueIsRejected() {
        assertThat(validator.validate(null)).isNull();
        assertThat(validator.validate("")).isNull();
        assertThat(validator.validate("   ")).isNull();
    }
}
