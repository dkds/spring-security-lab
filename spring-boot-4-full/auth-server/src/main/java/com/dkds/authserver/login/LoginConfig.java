package com.dkds.authserver.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.savedrequest.RequestCache;

/// Infrastructure beans for the form-login feature.
///
/// FormLoginConfigurer is declared as a bean (not `new`'d inline in
/// SecurityChains) for the same reason OneTimeTokenConfigurer is: it's a
/// mechanism-scoped AbstractHttpConfigurer, and SecurityChains should wire
/// every mechanism it composes the same way. This is not the "Do NOT declare
/// Customizer<HttpSecurity> beans for mechanisms" case DESIGN.md warns
/// about — that's about the functional Customizer<HttpSecurity> type, which
/// Spring Security auto-detects and applies ambiently to every chain.
/// FormLoginConfigurer's own type is never auto-applied; it only takes effect
/// where SecurityChains explicitly calls http.with(formLoginConfigurer, ...).
@Configuration
public class LoginConfig {

    @Bean
    public FormLoginConfigurer formLoginConfigurer(
            CaptchaService captchaService, RequestCache requestCache,
            SessionAuthenticationStrategy sessionAuthenticationStrategy, RememberMeServices rememberMeServices) {
        return new FormLoginConfigurer(captchaService, requestCache, sessionAuthenticationStrategy, rememberMeServices);
    }

    /// Built here rather than inline in FormLoginConfigurer purely so that
    /// class only ever sees the plain RememberMeServices interface, not the
    /// concrete PersistentTokenBasedRememberMeServices choice or its
    /// PersistentTokenRepository dependency. Only ever applied on Chain 3
    /// (FormLoginConfigurer) — NOT on Chain 1 (/oauth2/authorize), even
    /// though that's exactly where a remembered visitor's first hit lands.
    /// See SecurityChains' own comment: verified live that it wouldn't help
    /// there, due to how spring-security-oauth2-authorization-server orders
    /// its own internal filters.
    @Bean
    public RememberMeServices rememberMeServices(
            UserDetailsService userDetailsService, PersistentTokenRepository persistentTokenRepository,
            @Value("${app.security.remember-me-key}") String rememberMeKey) {
        return new PersistentTokenBasedRememberMeServices(rememberMeKey, userDetailsService, persistentTokenRepository);
    }
}
