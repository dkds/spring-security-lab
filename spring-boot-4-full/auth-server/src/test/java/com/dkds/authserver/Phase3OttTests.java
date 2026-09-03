package com.dkds.authserver;

import com.dkds.authserver.onetimetoken.NumericOneTimeTokenService;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 3 spike tests: MFA with One-Time Tokens.
///
/// Per DESIGN.md: this is the highest-risk phase.
/// Core spike: verify OTT generate/consume lifecycle works end-to-end via JPA.
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Phase 3: MFA with One-Time Tokens")
class Phase3OttTests {

    @Autowired
    private NumericOneTimeTokenService numericOneTimeTokenService;

    @Autowired
    private UserRepository userRepository;

    /// Prevent actual SMTP connection in tests.
    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("1. Numeric code generator produces six-digit codes")
    void numericCodeIsAlwaysSixDigits() {
        for (int i = 0; i < 100; i++) {
            var code = NumericOneTimeTokenService.generateNumericCode();
            assertThat(code)
                    .hasSize(6)
                    .matches("\\d{6}");
        }
    }

    @Test
    @DisplayName("2. OTT generate stores token in DB and returns six-digit code")
    void generateStoresTokenInDatabase() {
        var user = userRepository.findByUsername("user1").orElseThrow();
        var request = new GenerateOneTimeTokenRequest(user.getUsername());
        var token = numericOneTimeTokenService.generate(request);

        assertThat(token.getUsername()).isEqualTo("user1");
        assertThat(token.getTokenValue()).hasSize(6).matches("\\d{6}");
        assertThat(token.getExpiresAt()).isNotNull();

        // Token is in DB and can be consumed (single-use)
        var consumed = numericOneTimeTokenService.consume(
                new OneTimeTokenAuthenticationToken(token.getTokenValue()));
        assertThat(consumed).isNotNull();
        assertThat(consumed.getUsername()).isEqualTo("user1");
    }

    @Test
    @DisplayName("3. OTT is single-use: second consume returns null")
    void ottIsSingleUse() {
        var user = userRepository.findByUsername("user1").orElseThrow();
        var token = numericOneTimeTokenService.generate(
                new GenerateOneTimeTokenRequest(user.getUsername()));

        // First consume succeeds
        var first = numericOneTimeTokenService.consume(
                new OneTimeTokenAuthenticationToken(token.getTokenValue()));
        assertThat(first).isNotNull();

        // Second consume returns null (single-use enforced)
        var second = numericOneTimeTokenService.consume(
                new OneTimeTokenAuthenticationToken(token.getTokenValue()));
        assertThat(second).isNull();
    }

    @Test
    @DisplayName("4. Negative: invalid/non-existent token returns null")
    void invalidTokenReturnsNull() {
        var result = numericOneTimeTokenService.consume(
                new OneTimeTokenAuthenticationToken("000000"));
        assertThat(result).isNull();
    }
}
