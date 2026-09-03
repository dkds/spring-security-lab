package com.dkds.authserver.login;

import com.dkds.authserver.security.IdentityChangeAwareSessionStrategy;
import com.dkds.authserver.security.SecurityConstants;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
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
///   ChangeSessionIdAuthenticationStrategy). Do NOT use newSession().
public class FormLoginConfigurer
        extends AbstractHttpConfigurer<FormLoginConfigurer, HttpSecurity> {

    /// Matches requests carrying a Bearer token in the Authorization header.
    /// Public so SecurityChains can reuse it for Chain 1's own
    /// missing-FACTOR_OTT entry point — see the comment there.
    public static final RequestMatcher BEARER_TOKEN_MATCHER =
            new RequestHeaderRequestMatcher("Authorization", "Bearer ");

    @Override
    public void init(HttpSecurity http) {
        // Request cache scoped to the authorization endpoint. When an
        // unauthenticated user hits /oauth2/authorize, the request is saved and
        // replayed after a successful login. The identity-change session
        // strategy shares this exact instance so it clears the same saved
        // request when the principal changes.
        var requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(
                PathPatternRequestMatcher.withDefaults()
                        .matcher(SecurityConstants.OAUTH2_AUTHORIZE_MATCHER));

        var sessionStrategy = new IdentityChangeAwareSessionStrategy(requestCache);

        http
                .formLogin(form -> form
                        .loginPage(SecurityConstants.LOGIN_PAGE)
                        .loginProcessingUrl(SecurityConstants.LOGIN_PROCESSING_URL)
                        .permitAll())
                .requestCache(cache -> cache.requestCache(requestCache))
                .sessionManagement(session -> session
                        .sessionAuthenticationStrategy(sessionStrategy))
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(SecurityConstants.LOGIN_PAGE),
                        new NegatedRequestMatcher(BEARER_TOKEN_MATCHER)));
    }
}
