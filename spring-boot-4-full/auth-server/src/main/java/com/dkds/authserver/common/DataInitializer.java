package com.dkds.authserver.common;

import com.dkds.authserver.authorization.UserVerification;
import com.dkds.authserver.authorization.UserVerificationRepository;
import com.dkds.authserver.organization.*;
import com.dkds.authserver.sso.IdentityProvider;
import com.dkds.authserver.sso.IdentityProviderRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import com.dkds.authserver.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/// Seed data initialization, one independently-guarded method per phase —
/// see initializeData()'s own javadoc for why that matters.
///
/// seedPhase1Data() creates:
/// - 1 user in 1 organization
/// - 1 user in 3 organizations with different MFA modes: NEVER, EVERY\_30\_DAYS, DAILY
/// - 1 PLATFORM_ADMIN with at least one membership
/// - 1 IP-restricted organization
/// - 1 SSO-enabled organization (ORG_SSO; its IdentityProvider is seeded by seedPhase7SsoData())
///
/// seedPhase7SsoData() creates a SAML2 test user + membership in ORG_SSO +
/// the identity_provider row for the Keycloak "lab" realm.
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final OrgSecurityPolicyRepository orgSecurityPolicyRepository;
    private final OrgIpRangeRepository orgIpRangeRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final IdentityProviderRepository identityProviderRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.saml.keycloak-base-url}")
    private String keycloakBaseUrl;

    /// Each phase's seed data is guarded independently (checked for its own
    /// marker row, not "did Phase 1 already run") and appended here as its
    /// own private method — NOT nested inside seedPhase1Data()'s guard.
    /// Phase 7's SSO seed data originally lived at the end of that single
    /// method, gated by the SAME `user1` check as everything else — which
    /// meant it silently never ran on any database that had already been
    /// seeded before Phase 7 existed (an early `return` skips the entire
    /// rest of the method, new code included). Found the hard way: it
    /// worked on a fresh database and quietly did nothing on this
    /// devcontainer's already-seeded one. Every future phase's seed
    /// additions must follow this same one-guard-per-phase shape, not get
    /// appended inside an earlier phase's block.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeData() {
        seedPhase1Data();
        seedPhase7SsoData();
    }

    private void seedPhase1Data() {
        if (userRepository.findByUsername("user1").isPresent()) {
            log.info("Phase 1 seed data already initialized, skipping");
            return;
        }

        log.info("Initializing Phase 1 seed data...");

        // User 1: Single organization
        var user1 = AppUser.builder()
                .username("user1")
                .passwordHash(passwordEncoder.encode("password1"))
                .enabled(true)
                .failedAttempts(0)
                .build();
        user1 = userRepository.save(user1);

        var org1 = Organization.builder()
                .code("ORG001")
                .name("Test Organization 1")
                .active(true)
                .build();
        org1 = organizationRepository.save(org1);

        var membership1 = Membership.builder()
                .user(user1)
                .organization(org1)
                .active(true)
                .build();
        membershipRepository.save(membership1);

        var policy1 = OrgSecurityPolicy.builder()
                .orgId(org1.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policy1);

        log.info("Created user1 in org1 (ORG001)");

        // User 2: Three organizations with different MFA modes
        var user2 = AppUser.builder()
                .username("user2@dkds.com")
                .passwordHash(passwordEncoder.encode("password2"))
                .enabled(true)
                .failedAttempts(0)
                .build();
        user2 = userRepository.save(user2);

        // Org with NEVER MFA
        var org2 = Organization.builder()
                .code("ORG002")
                .name("Never MFA Organization")
                .active(true)
                .build();
        org2 = organizationRepository.save(org2);

        var membership2a = Membership.builder()
                .user(user2)
                .organization(org2)
                .active(true)
                .build();
        membershipRepository.save(membership2a);

        var policy2 = OrgSecurityPolicy.builder()
                .orgId(org2.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policy2);

        // Org with EVERY_30_DAYS MFA
        var org3 = Organization.builder()
                .code("ORG003")
                .name("Every 30 Days MFA Organization")
                .active(true)
                .build();
        org3 = organizationRepository.save(org3);

        var membership2b = Membership.builder()
                .user(user2)
                .organization(org3)
                .active(true)
                .build();
        membershipRepository.save(membership2b);

        var policy3 = OrgSecurityPolicy.builder()
                .orgId(org3.getId())
                .mfaMode(MfaMode.EVERY_30_DAYS)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policy3);

        // Org with DAILY MFA
        var org4 = Organization.builder()
                .code("ORG004")
                .name("Daily MFA Organization")
                .active(true)
                .build();
        org4 = organizationRepository.save(org4);

        var membership2c = Membership.builder()
                .user(user2)
                .organization(org4)
                .active(true)
                .build();
        membershipRepository.save(membership2c);

        var policy4 = OrgSecurityPolicy.builder()
                .orgId(org4.getId())
                .mfaMode(MfaMode.DAILY)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policy4);

        log.info("Created user2 in org2 (NEVER), org3 (EVERY_30_DAYS), org4 (DAILY)");

        // User 3: PLATFORM_ADMIN with at least one membership
        var admin = AppUser.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("adminpw"))
                .enabled(true)
                .failedAttempts(0)
                .role(UserRole.PLATFORM_ADMIN)
                .build();
        admin = userRepository.save(admin);

        var orgAdmin = Organization.builder()
                .code("ORG_ADMIN")
                .name("Admin Organization")
                .active(true)
                .build();
        orgAdmin = organizationRepository.save(orgAdmin);

        var membershipAdmin = Membership.builder()
                .user(admin)
                .organization(orgAdmin)
                .active(true)
                .build();
        membershipRepository.save(membershipAdmin);

        var policyAdmin = OrgSecurityPolicy.builder()
                .orgId(orgAdmin.getId())
                .mfaMode(MfaMode.EVERY_LOGIN)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policyAdmin);

        log.info("Created admin user in orgAdmin (ORG_ADMIN) with EVERY_LOGIN MFA");

        // IP-restricted organization
        var orgIpRestricted = Organization.builder()
                .code("ORG_IP_RESTRICTED")
                .name("IP Restricted Organization")
                .active(true)
                .build();
        orgIpRestricted = organizationRepository.save(orgIpRestricted);

        var policyIpRestricted = OrgSecurityPolicy.builder()
                .orgId(orgIpRestricted.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(true)
                .build();
        orgSecurityPolicyRepository.save(policyIpRestricted);

        var ipRange1 = OrgIpRange.builder()
                .organization(orgIpRestricted)
                .cidr("192.168.1.0/24")
                .build();
        orgIpRangeRepository.save(ipRange1);

        var ipRange2 = OrgIpRange.builder()
                .organization(orgIpRestricted)
                .cidr("10.0.0.0/8")
                .build();
        orgIpRangeRepository.save(ipRange2);

        log.info("Created IP-restricted organization (ORG_IP_RESTRICTED) with CIDR blocks");

        // SSO-enabled organization (IdentityProvider will be added in Phase 7)
        var orgSso = Organization.builder()
                .code("ORG_SSO")
                .name("SSO Organization")
                .active(true)
                .build();
        orgSso = organizationRepository.save(orgSso);

        var policySso = OrgSecurityPolicy.builder()
                .orgId(orgSso.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(false)
                .build();
        orgSecurityPolicyRepository.save(policySso);

        log.info("Created SSO organization (ORG_SSO) ready for IdP configuration");

        // Add some user verifications
        var user1EmailVerif = UserVerification.builder()
                .user(user1)
                .method(UserVerification.METHOD_EMAIL)
                .verifiedAt(Instant.now())
                .build();
        userVerificationRepository.save(user1EmailVerif);

        var user2EmailVerif = UserVerification.builder()
                .user(user2)
                .method(UserVerification.METHOD_EMAIL)
                .verifiedAt(Instant.now())
                .build();
        userVerificationRepository.save(user2EmailVerif);

        log.info("Phase 1 seed data initialization complete");
    }

    private void seedPhase7SsoData() {
        if (userRepository.findByUsername("ssouser@dkds.com").isPresent()) {
            log.info("Phase 7 SSO seed data already initialized, skipping");
            return;
        }

        log.info("Initializing Phase 7 SSO seed data...");

        // ORG_SSO is seeded by seedPhase1Data(), independently guarded from
        // this method — look it up rather than relying on a local variable
        // from that method, since this method must also work standalone
        // when seedPhase1Data() was skipped (already-seeded database).
        var orgSso = organizationRepository.findByCode("ORG_SSO")
                .orElseThrow(() -> new IllegalStateException(
                        "ORG_SSO organization must already exist (seeded by seedPhase1Data) before seeding SSO data"));

        // SAML2 test user for ORG_SSO. Password login is never actually
        // exercised for this account — passwordHash is set purely to
        // satisfy the NOT NULL column — but a real hash is used anyway
        // rather than a sentinel value, so nothing here looks load-bearing
        // for a login path it isn't meant to support.
        var ssoUser = AppUser.builder()
                .username("ssouser@dkds.com")
                .passwordHash(passwordEncoder.encode("not-used-saml-only"))
                .enabled(true)
                .failedAttempts(0)
                .build();
        ssoUser = userRepository.save(ssoUser);

        var membershipSso = Membership.builder()
                .user(ssoUser)
                .organization(orgSso)
                .active(true)
                .build();
        membershipRepository.save(membershipSso);

        // registrationId "keycloak" matches the redirectUri/ACS URL baked
        // into the Keycloak "lab" realm's SAML client
        // (auth-server/docker/keycloak/lab-realm.json) — see
        // Saml2Configurer/DatabaseRelyingPartyRegistrationRepository. The
        // certificate is the realm's own fixed, lab-only RSA signing cert
        // (same rationale as auth-server's own jwt-signing-key.pem): without
        // a fixed key, Keycloak would generate a random one per environment,
        // and this seeded value would need to be re-synced by hand every
        // time.
        var identityProvider = IdentityProvider.builder()
                .registrationId("keycloak")
                .orgId(orgSso.getId())
                .entityId(keycloakBaseUrl + "/realms/lab")
                .ssoUrl(keycloakBaseUrl + "/realms/lab/protocol/saml")
                .certificate("""
                        -----BEGIN CERTIFICATE-----
                        MIIDBTCCAe2gAwIBAgIUS8TeFUTJ5rVxubEMPrZqkAdBL+YwDQYJKoZIhvcNAQEL
                        BQAwEjEQMA4GA1UEAwwHbGFiLWlkcDAeFw0yNjA5MDQwNDEyNTJaFw0zNjA5MDEw
                        NDEyNTJaMBIxEDAOBgNVBAMMB2xhYi1pZHAwggEiMA0GCSqGSIb3DQEBAQUAA4IB
                        DwAwggEKAoIBAQC8O05JbczCLsGSk6smNm9yPz1OKcBLt3CmIePnaLfDAGvqsMeq
                        fXVjCSInPOnT2+rhLZLX8wy1BzPgBayEUTXm1lt6X95BdtVLQSlnCaTyNuF4LRFP
                        tnLtFmIgJPOoflcCFUr9s1Zy6JHuGA2PEjfBJdN8hS+on4v6a1leiZgVs+WdIVcm
                        EBmH9DjKqpPIaycmyUqCRgdt6P9EVVpv2145907pZHalidrIif6ypJZW0NFO0rb9
                        cXwqArs4Kkul77E3+rQUx3FHVftz0bCoqCynX5rUfWemnLHL8f1h56rWzVVrG7Q0
                        ZhFFzC/sJuvJ14/PYT3sx7kWeNLvBTbpiimDAgMBAAGjUzBRMB0GA1UdDgQWBBSf
                        xlIN0KEa1wRWHhNl1cNo1KDDfjAfBgNVHSMEGDAWgBSfxlIN0KEa1wRWHhNl1cNo
                        1KDDfjAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQC0HQ/X3Rfe
                        /kvwbVRq0LjAaIyTEswtL2k57hbcVmp7Q7QLlqrcV97DVfAGPWEgTJcuG8D9TjkG
                        BOx9UfQeLcb2nWfm3fa2nDjXBEqnWKngP2VoyeoyfcDOjXk8oNO4ggdWRUcShezw
                        AqJGEKnGeMx7U+yoVIQtNXGJbrtJjaYJokH7on+DUx0Css8S2CgO6ITGsnbO6dng
                        zXyLDDzuTRcgJteakl7o8W1+BfEwxlP8x2ZpbwhX45EfAuA3AWUpLFN2rLsw9Gp4
                        cZvGW9rFGdCiwuKTQ+6ve1UqZr0BRQ/CTq38gA44tb+WmTIW8hMCf9RUIx/Wa32t
                        cq+N1q/R8Jdc
                        -----END CERTIFICATE-----
                        """)
                .active(true)
                .build();
        identityProviderRepository.save(identityProvider);

        log.info("Created SAML2 test user (ssouser@dkds.com) and identity_provider row for ORG_SSO");
    }
}
