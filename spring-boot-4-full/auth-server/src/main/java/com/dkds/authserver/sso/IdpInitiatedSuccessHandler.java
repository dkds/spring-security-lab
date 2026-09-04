package com.dkds.authserver.sso;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.RequestCache;

import java.io.IOException;

/// Per DESIGN.md: "IdP-initiated is the primary path. On an assertion with
/// no saved request, redirect to the SPA landing route, which starts a fresh
/// authorization code request with PKCE."
///
/// - A saved request present (SP-initiated: the browser hit /oauth2/authorize
///   first, got sent to /login, chose SSO) — resume it, same as today.
/// - No saved request (a genuinely unsolicited assertion, or a direct visit
///   to the IdP) — redirect to a validated RelayState target if the IdP
///   supplied one, else the fixed SPA landing URI. Landing on the SPA's own
///   root is enough: RequireAuth.tsx fires signinRedirect() the moment an
///   unauthenticated visitor lands on a protected route, which is exactly
///   "starts a fresh authorization code request with PKCE" — no extra UI
///   work needed, verified by reading the actual SPA routing.
public class IdpInitiatedSuccessHandler implements AuthenticationSuccessHandler {

    private final RequestCache requestCache;
    private final RelayStateValidator relayStateValidator;
    private final String spaLandingUri;
    private final AuthenticationSuccessHandler savedRequestHandler;

    public IdpInitiatedSuccessHandler(RequestCache requestCache, RelayStateValidator relayStateValidator,
                                       String spaLandingUri) {
        this.requestCache = requestCache;
        this.relayStateValidator = relayStateValidator;
        this.spaLandingUri = spaLandingUri;
        var savedRequestHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        savedRequestHandler.setRequestCache(requestCache);
        this.savedRequestHandler = savedRequestHandler;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {
        if (requestCache.getRequest(request, response) != null) {
            savedRequestHandler.onAuthenticationSuccess(request, response, authentication);
            return;
        }
        String target = relayStateValidator.validate(request.getParameter("RelayState"));
        response.sendRedirect(target != null ? target : spaLandingUri);
    }
}
