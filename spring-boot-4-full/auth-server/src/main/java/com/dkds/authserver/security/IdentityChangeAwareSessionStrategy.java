package com.dkds.authserver.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.savedrequest.RequestCache;

/// Session strategy that is aware of identity changes.
///
/// Per DESIGN.md:
/// - Delegates to ChangeSessionIdAuthenticationStrategy for session-fixation
///   protection.
/// - When the incoming principal differs from the current one, clears the saved
///   request first so a stale SP-initiated authorization request is not replayed
///   for a different identity.
/// - Do NOT use sessionFixation().newSession() — it discards the saved request
///   on every login and breaks SP-initiated SSO.
///
/// Shares the same RequestCache instance used by the form-login configurer so
/// that the saved /oauth2/authorize request is the one being cleared.
public class IdentityChangeAwareSessionStrategy implements SessionAuthenticationStrategy {

    private final ChangeSessionIdAuthenticationStrategy delegate =
            new ChangeSessionIdAuthenticationStrategy();
    private final RequestCache requestCache;

    public IdentityChangeAwareSessionStrategy(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
                                 HttpServletResponse response) {
        var existing = SecurityContextHolder.getContext().getAuthentication();

        boolean identityChanged = existing != null
                && existing.isAuthenticated()
                && !existing.getName().equals(authentication.getName());

        if (identityChanged) {
            requestCache.removeRequest(request, response);
        }

        delegate.onAuthentication(authentication, request, response);
    }
}
