package com.dkds.authserver.onetimetoken;

import com.dkds.authserver.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;

/// Feature configurer for OTT-based MFA (Chain 3).
///
/// Per DESIGN.md:
/// - Ships as an AbstractHttpConfigurer in its own feature package.
/// - Applied via http.with(...) in SecurityChains.
/// - showDefaultSubmitPage(false) with a custom entry screen at /ott/input.
/// - Uses NumericOneTimeTokenService (six-digit codes, not magic links).
/// - Uses JdbcOneTimeTokenService for storage (no otp_challenge table).
@RequiredArgsConstructor
public class OneTimeTokenConfigurer
        extends AbstractHttpConfigurer<OneTimeTokenConfigurer, HttpSecurity> {

    private final OneTimeTokenService oneTimeTokenService;
    private final OneTimeTokenGenerationSuccessHandler deliveryHandler;

    @Override
    public void init(HttpSecurity http) {
        // defaultSubmitPageUrl(...) is deliberately not called: it always
        // re-invokes showDefaultSubmitPage(true) internally, which would
        // silently re-enable the built-in page depending on call order. The
        // custom entry screen is served entirely by LoginController at
        // EmailOttDeliveryHandler.OTT_INPUT_URL.
        //
        // loginPage(...) must match FormLoginConfigurer's, even though OTT
        // never redirects there itself: without a custom login page set here,
        // this mechanism's own isCustomLoginPage() stays false, so Spring
        // marks the shared DefaultLoginPageGeneratingFilter as enabled
        // (setOneTimeTokenEnabled(true)) and — since nothing on this chain
        // sets ExceptionHandlingConfigurer's plain entry point, only the
        // matcher-scoped one DESIGN.md requires — that filter gets added to
        // Chain 3 and answers GET /login before LoginController ever runs.
        http.oneTimeTokenLogin(ott -> ott
                .tokenService(oneTimeTokenService)
                .tokenGenerationSuccessHandler(deliveryHandler)
                .showDefaultSubmitPage(false)
                .loginPage(SecurityConstants.LOGIN_PAGE)
        );
    }
}
