package com.dkds.authserver.sso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/// Validates a RelayState value as untrusted input before it's used as a
/// redirect target (DESIGN.md: "Validate RelayState as untrusted input
/// before redirecting (host allowlist)"). Used by IdpInitiatedSuccessHandler
/// to let an unsolicited assertion's RelayState optionally name a specific
/// SPA-relative landing page, without opening an arbitrary-redirect hole —
/// a RelayState naming any origin other than the SPA's own is rejected, not
/// followed.
///
/// The allowlisted origin is derived from app.oauth2.spa-landing-uri (the
/// same property IdpInitiatedSuccessHandler falls back to) rather than a
/// separate property — one source of truth for "what the SPA's origin is",
/// not two properties that could drift apart.
@Component
public class RelayStateValidator {

    private final String allowedOrigin;

    public RelayStateValidator(@Value("${app.oauth2.spa-landing-uri}") String spaLandingUri) {
        URI landing = URI.create(spaLandingUri);
        this.allowedOrigin = landing.getScheme() + "://" + landing.getAuthority();
    }

    /// Returns relayState itself if it's an absolute URL on the allowlisted
    /// origin; null otherwise (malformed, relative, or a foreign host).
    public String validate(String relayState) {
        if (relayState == null || relayState.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(relayState);
        } catch (URISyntaxException ex) {
            return null;
        }
        if (!uri.isAbsolute()) {
            return null;
        }
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        return allowedOrigin.equals(origin) ? relayState : null;
    }
}
