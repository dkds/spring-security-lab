package com.dkds.authserver.login;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public FormLoginConfigurer formLoginConfigurer(CaptchaService captchaService) {
        return new FormLoginConfigurer(captchaService);
    }
}
