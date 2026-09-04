package com.dkds.authserver.sso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.IterableRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/// Loads RelyingPartyRegistrations from the `identity_provider` table, one per
/// active row, keyed by registration_id.
///
/// Per DESIGN.md: "a short-TTL cache". Deliberately a small hand-rolled cache
/// rather than Spring Security's own CachingRelyingPartyRegistrationRepository
/// (which delegates to org.springframework.cache.Cache — pulling in a real
/// cache provider like Caffeine for what amounts to one cached snapshot,
/// re-queried every few seconds, is more machinery than this needs).
///
/// SP-side entityId/assertionConsumerServiceLocation are left at Spring's own
/// defaults ("{baseUrl}/saml2/service-provider-metadata/{registrationId}" and
/// "{baseUrl}/login/saml2/sso/{registrationId}", resolved from the actual
/// incoming request at authentication time) rather than overridden here —
/// auth-server is always reachable at the same http://localhost:9000 in every
/// run mode this repo has (see compose.yaml's own notes), so the Keycloak
/// realm's registered SP URLs never need to change per profile.
///
/// wantAuthnRequestsSigned is explicitly forced to false: the Keycloak client
/// this talks to has saml.client.signature=false (verified against a live
/// instance — Keycloak's own advertised WantAuthnRequestsSigned="true" in its
/// IDPSSODescriptor is a fixed/informational value, not real per-client
/// enforcement), and the SP has no signing credential configured, so leaving
/// this at RelyingPartyRegistration.Builder's own default of true would make
/// Spring attempt to sign AuthnRequests with a credential that doesn't exist.
@Component
@Slf4j
public class DatabaseRelyingPartyRegistrationRepository implements IterableRelyingPartyRegistrationRepository {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final IdentityProviderRepository identityProviderRepository;
    private final Clock clock;

    private volatile IterableRelyingPartyRegistrationRepository cached = EMPTY;
    private volatile Instant expiresAt = Instant.MIN;

    private static final IterableRelyingPartyRegistrationRepository EMPTY = new IterableRelyingPartyRegistrationRepository() {
        @Override
        public Iterator<RelyingPartyRegistration> iterator() {
            return Collections.emptyIterator();
        }

        @Override
        public RelyingPartyRegistration findByRegistrationId(String registrationId) {
            return null;
        }
    };

    public DatabaseRelyingPartyRegistrationRepository(
            IdentityProviderRepository identityProviderRepository, Clock clock) {
        this.identityProviderRepository = identityProviderRepository;
        this.clock = clock;
    }

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        return current().findByRegistrationId(registrationId);
    }

    @Override
    public Iterator<RelyingPartyRegistration> iterator() {
        return current().iterator();
    }

    private synchronized IterableRelyingPartyRegistrationRepository current() {
        if (clock.instant().isAfter(expiresAt)) {
            cached = load();
            expiresAt = clock.instant().plus(CACHE_TTL);
        }
        return cached;
    }

    private IterableRelyingPartyRegistrationRepository load() {
        List<RelyingPartyRegistration> registrations = identityProviderRepository.findByActiveTrue().stream()
                .map(DatabaseRelyingPartyRegistrationRepository::toRegistration)
                .toList();
        if (registrations.isEmpty()) {
            log.warn("No active identity_provider rows; SAML2 login has no relying party registrations");
            return EMPTY;
        }
        return new InMemoryRelyingPartyRegistrationRepository(registrations);
    }

    private static RelyingPartyRegistration toRegistration(IdentityProvider idp) {
        X509Certificate certificate = parseCertificate(idp.getCertificate());
        return RelyingPartyRegistration.withRegistrationId(idp.getRegistrationId())
                .assertingPartyMetadata(apm -> apm
                        .entityId(idp.getEntityId())
                        .singleSignOnServiceLocation(idp.getSsoUrl())
                        .wantAuthnRequestsSigned(false)
                        .verificationX509Credentials(c -> c.add(Saml2X509Credential.verification(certificate))))
                .build();
    }

    private static X509Certificate parseCertificate(String pem) {
        try (var in = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8))) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid identity_provider certificate", ex);
        }
    }
}
