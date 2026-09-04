package com.dkds.authserver.login;

import com.dkds.authserver.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestHeaderRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/// Feature configurer for form login (Chain 3).
///
/// Per DESIGN.md:
/// - Each mechanism ships an AbstractHttpConfigurer in its own feature package,
///   applied by the composition root via http.with(...).
/// - Do NOT declare Customizer<HttpSecurity> beans for mechanisms (would leak
///   form login into chains 1 and 2).
/// - Entry point: LoginUrlAuthenticationEntryPoint matched by
///   NegatedRequestMatcher(BEARER_TOKEN_MATCHER). Do NOT use
///   MediaTypeRequestMatcher(TEXT_HTML).
/// - HttpSessionRequestCache with matcher /oauth2/authorize.
/// - Session handling uses IdentityChangeAwareSessionStrategy (delegating to
///   ChangeSessionIdAuthenticationStrategy). Do NOT use newSession(). Built by
///   SecurityChains (ArchUnit rule 3: `security` may depend on anything,
///   nothing depends on it) and injected here only by its plain
///   SessionAuthenticationStrategy interface type — this class never names
///   the concrete class.
/// - Phase 9: CaptchaFilter runs before UsernamePasswordAuthenticationFilter,
///   here only — never on Chain 1/2 (see Phase9ChainInventoryTests).
@RequiredArgsConstructor
public class FormLoginConfigurer
        extends AbstractHttpConfigurer<FormLoginConfigurer, HttpSecurity> {

    /// Matches requests carrying a Bearer token in the Authorization header.
    /// Public so SecurityChains can reuse it for Chain 1's own
    /// missing-FACTOR_OTT entry point — see the comment there.
    public static final RequestMatcher BEARER_TOKEN_MATCHER =
            new RequestHeaderRequestMatcher("Authorization", "Bearer ");

    private final CaptchaService captchaService;

    /// Scoped to the authorization endpoint. When an unauthenticated user
    /// hits /oauth2/authorize, the request is saved and replayed after a
    /// successful login. sessionAuthenticationStrategy shares this exact
    /// instance so it clears the same saved request when the principal
    /// changes.
    private final RequestCache requestCache;

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    @Override
    public void init(HttpSecurity http) {
        http
                .formLogin(form -> form
                        .loginPage(SecurityConstants.LOGIN_PAGE)
                        .loginProcessingUrl(SecurityConstants.LOGIN_PROCESSING_URL)
                        // alwaysUse=false (the one-arg overload): a saved
                        // request — e.g. the SPA's /oauth2/authorize hit that
                        // sent the browser here — still wins and gets
                        // resumed. This is only the fallback for a login with
                        // nothing saved (direct browser navigation to
                        // /login, or a login right after logout cleared the
                        // old saved request): without it,
                        // SavedRequestAwareAuthenticationSuccessHandler falls
                        // back to its own default of "/", which 404s since
                        // this auth-server-only app has no root controller.
                        .defaultSuccessUrl(SecurityConstants.LOGIN_SUCCESS_URL)
                        .permitAll())
                .requestCache(cache -> cache.requestCache(requestCache))
                .sessionManagement(session -> session
                        .sessionAuthenticationStrategy(sessionAuthenticationStrategy))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(SecurityConstants.LOGIN_PAGE),
                        new NegatedRequestMatcher(BEARER_TOKEN_MATCHER)))
                .addFilterBefore(new CaptchaFilter(captchaService), UsernamePasswordAuthenticationFilter.class);
    }
}
