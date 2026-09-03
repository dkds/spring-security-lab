package com.dkds.authserver.common;

import com.dkds.authserver.authorization.UserVerification;
import com.dkds.authserver.authorization.UserVerificationRepository;
import com.dkds.authserver.organization.*;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/// Phase 1 seed data initialization.
/// Creates:
/// - 1 user in 1 organization
/// - 1 user in 3 organizations with different MFA modes: NEVER, EVERY\_30\_DAYS, DAILY
/// - 1 PLATFORM_ADMIN with at least one membership
/// - 1 IP-restricted organization
/// - 1 SSO-enabled organization (IdentityProvider to be added in Phase 7)
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
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeData() {
        if (userRepository.findByUsername("user1").isPresent()) {
            log.info("Data already initialized, skipping");
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
}
