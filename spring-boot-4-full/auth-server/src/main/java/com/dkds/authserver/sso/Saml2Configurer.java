package com.dkds.authserver.sso;

import com.dkds.authserver.security.SecurityConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.web.savedrequest.RequestCache;

/// Feature configurer for SAML2 login (Chain 3) — SP-initiated (Phase 7) and
/// IdP-initiated (Phase 8).
///
/// Per DESIGN.md:
/// - Ships as an AbstractHttpConfigurer in its own feature package, applied
///   via http.with(...) in SecurityChains — same shape as
///   FormLoginConfigurer/OneTimeTokenConfigurer.
/// - Form login, OTT and SAML2 all live in Chain 3 only.
/// - Same UserDetailsChecker (here: SamlUserAuthoritiesConverter) applied in
///   the response authentication converter as the password path.
/// - AssertionReplayGuard wired as the assertion validator — see its own
///   javadoc for why Spring's built-in validators don't cover this on their
///   own once unsolicited (IdP-initiated) assertions are accepted.
/// - IdpInitiatedSuccessHandler: saved request present -> resume it (same as
///   today); none -> the SPA landing route, per RelayState if
///   RelayStateValidator accepts it.
///
/// loginPage(...) is set explicitly to SecurityConstants.LOGIN_PAGE, even
/// though this app has only one identity_provider row today. Without it,
/// Saml2LoginConfigurer's "exactly one relying party registration" branch
/// auto-registers ITS OWN default AuthenticationEntryPoint for the chain
/// (straight to the IdP) via the same generic
/// ExceptionHandlingConfigurer.defaultAuthenticationEntryPointFor(...)
/// mechanism FormLoginConfigurer and OneTimeTokenConfigurer already use —
/// confirmed by reading Saml2LoginConfigurer/AbstractAuthenticationFilterConfigurer
/// source. Because SecurityChains applies this configurer AFTER
/// formLoginConfigurer, and DelegatingAuthenticationEntryPoint picks the
/// first registered match, form login's own broad
/// NegatedRequestMatcher(BEARER_TOKEN_MATCHER) entry point already wins for
/// every ordinary unauthenticated request regardless — but setting an
/// explicit loginPage here avoids relying on that registration-order
/// argument implicitly, and (per DESIGN.md's own convention) keeps every
/// mechanism in this chain configured the same explicit way rather than one
/// depending on a branch inside library defaults.
///
/// A user reaches SP-initiated SAML by navigating to
/// /saml2/authenticate/{registrationId} (Spring's own default initiation
/// endpoint) — no custom entry point or controller is needed to trigger it.
/// See LoginController's template for the "Sign in with SSO" link.
/// IdP-initiated is now the primary path per DESIGN.md: a validly-signed
/// assertion posted straight to /login/saml2/sso/{registrationId}, with no
/// prior visit to auth-server at all, authenticates just the same.
@RequiredArgsConstructor
public class Saml2Configurer extends AbstractHttpConfigurer<Saml2Configurer, HttpSecurity> {

    private final RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;
    private final SamlUserAuthoritiesConverter samlUserAuthoritiesConverter;
    private final AssertionReplayGuard assertionReplayGuard;
    private final RelayStateValidator relayStateValidator;
    private final String spaLandingUri;

    @Override
    public void init(HttpSecurity http) {
        var responseAuthenticationConverter = new OpenSaml5AuthenticationProvider.ResponseAuthenticationConverter();
        responseAuthenticationConverter.setGrantedAuthoritiesConverter(samlUserAuthoritiesConverter);

        var provider = new OpenSaml5AuthenticationProvider();
        provider.setResponseAuthenticationConverter(responseAuthenticationConverter);
        provider.setAssertionValidator(assertionReplayGuard);

        // The SAME RequestCache instance FormLoginConfigurer publishes as
        // Chain 3's shared object during its own (earlier-run) init() — so
        // "was there a saved /oauth2/authorize request" means the same thing
        // on the password and SAML paths. Spring's own Saml2LoginConfigurer
        // reads this shared object the identical way for its default
        // handler; safe to rely on the same mechanism here.
        var requestCache = http.getSharedObject(RequestCache.class);

        http.saml2Login(saml2 -> saml2
                .relyingPartyRegistrationRepository(relyingPartyRegistrationRepository)
                .loginPage(SecurityConstants.LOGIN_PAGE)
                .authenticationManager(new ProviderManager(provider))
                .successHandler(new IdpInitiatedSuccessHandler(requestCache, relayStateValidator, spaLandingUri)));
    }
}
