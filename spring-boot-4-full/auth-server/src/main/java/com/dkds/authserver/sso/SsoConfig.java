package com.dkds.authserver.sso;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Infrastructure beans for the SAML2 SSO feature.
///
/// Saml2Configurer is declared as a bean, not `new`'d inline in
/// SecurityChains, for the same reason FormLoginConfigurer/
/// OneTimeTokenConfigurer are (see LoginConfig).
@Configuration
public class SsoConfig {

    @Bean
    public Saml2Configurer saml2Configurer(
            DatabaseRelyingPartyRegistrationRepository relyingPartyRegistrationRepository,
            SamlUserAuthoritiesConverter samlUserAuthoritiesConverter,
            AssertionReplayGuard assertionReplayGuard,
            RelayStateValidator relayStateValidator,
            @Value("${app.oauth2.spa-landing-uri}") String spaLandingUri) {
        return new Saml2Configurer(relyingPartyRegistrationRepository, samlUserAuthoritiesConverter,
                assertionReplayGuard, relayStateValidator, spaLandingUri);
    }
}
