package com.dkds.authserver.onetimetoken;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.ott.OneTimeTokenAuthenticationFilter;
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
    private final OttAuthenticationFailureHandler failureHandler;
    private final AuthenticatedGenerateOneTimeTokenRequestResolver generateRequestResolver;

    @Override
    public void init(HttpSecurity http) {
        // defaultSubmitPageUrl(...) is deliberately not called: it always
        // re-invokes showDefaultSubmitPage(true) internally, which would
        // silently re-enable the built-in page depending on call order. The
        // custom entry screen is served entirely by LoginController at
        // EmailOttDeliveryHandler.OTT_INPUT_URL.
        //
        // loginPage(...) must be called with SOME custom page here, even
        // though its exact value only matters for one of two reasons this
        // mechanism needs it:
        //
        // 1. Without ANY custom login page set, this mechanism's own
        //    isCustomLoginPage() stays false, so Spring marks the shared
        //    DefaultLoginPageGeneratingFilter as enabled
        //    (setOneTimeTokenEnabled(true)) and — since nothing on this chain
        //    sets ExceptionHandlingConfigurer's plain entry point, only the
        //    matcher-scoped one DESIGN.md requires — that filter gets added
        //    to Chain 3 and answers GET /login before LoginController ever
        //    runs.
        // 2. The value itself becomes this configurer's own
        //    missing-FACTOR_OTT entry point target. It must NOT be
        //    SecurityConstants.LOGIN_PAGE: that page only renders a
        //    username/password form, and a principal who lands there missing
        //    FACTOR_OTT has already authenticated with a password —
        //    resubmitting that form just re-authenticates and loops back to
        //    the same denial. EmailOttDeliveryHandler.OTT_REQUEST_URL is a
        //    dedicated page for exactly this state.
        //
        // loginProcessingUrl(...) must be pinned back explicitly, AFTER
        // loginPage(...): loginPage(...) internally calls
        // updateAuthenticationDefaults(), which — finding the processing URL
        // still unset at that point — virtually dispatches back into this
        // configurer's own loginProcessingUrl(...) override and sets
        // OneTimeTokenAuthenticationFilter's actual matcher to
        // OTT_REQUEST_URL (the page value) instead of the real submission
        // endpoint /login/ott. Without this, OneTimeTokenLoginConfigurer's
        // own init()-time default (`if (getLoginProcessingUrl() == null)`)
        // never fires, since the URL is no longer null by the time it runs —
        // codes get typed on /ott/input, POSTed to /login/ott per that
        // template, and silently fall through the filter chain unvalidated.
        http.oneTimeTokenLogin(ott -> ott
                .tokenService(oneTimeTokenService)
                .tokenGenerationSuccessHandler(deliveryHandler)
                .showDefaultSubmitPage(false)
                .loginPage(EmailOttDeliveryHandler.OTT_REQUEST_URL)
                .loginProcessingUrl(OneTimeTokenAuthenticationFilter.DEFAULT_LOGIN_PROCESSING_URL)
                .failureHandler(failureHandler)
                // GenerateOneTimeTokenFilter runs ahead of AuthorizationFilter
                // in the chain, so it never sees SecurityChains' access(...)
                // rule for /ott/generate. This resolver is what actually
                // keeps an anonymous caller from minting a code for an
                // arbitrary username — see its own javadoc.
                .generateRequestResolver(generateRequestResolver)
        );
    }
}
