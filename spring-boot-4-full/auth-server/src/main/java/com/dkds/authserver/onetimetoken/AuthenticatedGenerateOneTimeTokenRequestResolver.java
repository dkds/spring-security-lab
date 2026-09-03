package com.dkds.authserver.onetimetoken;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.ott.GenerateOneTimeTokenRequestResolver;

import java.time.Duration;

/// Resolves the OTT generation request from the caller's own authenticated
/// principal instead of Spring's default, which trusts an unauthenticated
/// "username" request parameter (see DefaultGenerateOneTimeTokenRequestResolver
/// — it's meant for magic-link-style login flows where nobody is logged in
/// yet). GenerateOneTimeTokenFilter runs before AnonymousAuthenticationFilter
/// and AuthorizationFilter in the chain (it's registered directly by
/// OneTimeTokenLoginConfigurer ahead of the authorization filter), so
/// requestMatchers(...).access(...) rules in SecurityChains never get a
/// chance to run for POST /ott/generate — without this resolver, any
/// anonymous caller could mint (and have emailed) a real code for any
/// username by POSTing here directly.
///
/// Returning null here — same as an empty "username" param would with the
/// default resolver — makes GenerateOneTimeTokenFilter fall through to the
/// rest of the chain instead of generating anything, so an unauthenticated
/// caller lands on AuthorizationFilter and gets redirected to login like any
/// other denied request.
///
/// The AnonymousAuthenticationToken check is defense-in-depth: a fresh
/// anonymous request has no SecurityContext yet at this point in the chain
/// (AnonymousAuthenticationFilter hasn't run), so getAuthentication() is
/// simply null in practice — but nothing here should rely on filter
/// ordering alone to keep anonymous callers out.
public class AuthenticatedGenerateOneTimeTokenRequestResolver implements GenerateOneTimeTokenRequestResolver {

    private static final Duration EXPIRES_IN = Duration.ofMinutes(5);

    @Override
    public GenerateOneTimeTokenRequest resolve(HttpServletRequest request) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return new GenerateOneTimeTokenRequest(authentication.getName(), EXPIRES_IN);
    }
}
