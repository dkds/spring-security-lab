package com.dkds.authserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/// The request cache FormLoginConfigurer and Saml2Configurer share (the
/// latter via http.getSharedObject(RequestCache.class), published
/// automatically once FormLoginConfigurer's own .requestCache(...) DSL call
/// runs) so an /oauth2/authorize hit saved before login is the exact one
/// resumed afterward, on either path.
///
/// A standalone @Configuration, not @Bean methods on SecurityChains itself
/// (ArchUnit, Phase 11): constructing IdentityChangeAwareSessionStrategy
/// requires naming that concrete class, and DESIGN.md's rule 3 forbids
/// anything outside `security` from depending on it — login only ever sees
/// the two beans below through their plain Spring Security interface types.
/// Kept separate from SecurityChains specifically to avoid a circular
/// dependency: SecurityChains' constructor needs the FormLoginConfigurer
/// bean, which needs these two beans — if they were @Bean methods on
/// SecurityChains itself, Spring would have to finish constructing
/// SecurityChains before it could call them to satisfy FormLoginConfigurer,
/// which is needed to construct SecurityChains.
@Configuration
public class SessionRequestCacheConfig {

    @Bean
    public RequestCache requestCache() {
        var requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(SecurityConstants.OAUTH2_AUTHORIZE_MATCHER));
        return requestCache;
    }

    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(RequestCache requestCache) {
        return new IdentityChangeAwareSessionStrategy(requestCache);
    }
}
