package com.dkds.authserver;

import com.dkds.authserver.sso.AssertionReplayGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 8, PLAN.md test 1: "Replayed assertion rejected on second
/// presentation."
///
/// Exercises isReplay(String) directly — see AssertionReplayGuard's own
/// javadoc for why: AssertionToken's constructors are package-private to
/// spring-security-saml2's own package, and a bare mock of it would still
/// need to satisfy the real cryptographic default validator convert(...)
/// delegates to first, making a clean unit test of full crypto impossible
/// here. The full wiring (real signed assertion, replayed twice against a
/// live Keycloak instance) was verified live instead.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 8: AssertionReplayGuard")
class Phase8AssertionReplayGuardTests {

    @Autowired
    private AssertionReplayGuard assertionReplayGuard;

    @Test
    @DisplayName("The same assertion ID is accepted once, then rejected as a replay on every subsequent presentation")
    void secondPresentationOfSameIdIsRejected() {
        String assertionId = "phase8-replay-test-id";

        assertThat(assertionReplayGuard.isReplay(assertionId)).as("first presentation").isFalse();
        assertThat(assertionReplayGuard.isReplay(assertionId)).as("second presentation").isTrue();
        assertThat(assertionReplayGuard.isReplay(assertionId)).as("third presentation").isTrue();
    }

    @Test
    @DisplayName("Different assertion IDs do not interfere with each other")
    void differentIdsAreIndependent() {
        assertThat(assertionReplayGuard.isReplay("phase8-replay-test-id-a")).isFalse();
        assertThat(assertionReplayGuard.isReplay("phase8-replay-test-id-b")).isFalse();
        assertThat(assertionReplayGuard.isReplay("phase8-replay-test-id-a")).isTrue();
    }
}
