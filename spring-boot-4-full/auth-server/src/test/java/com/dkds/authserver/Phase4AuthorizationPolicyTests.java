package com.dkds.authserver;

import com.dkds.authserver.authorization.OrgPolicyRequiredAuthoritiesRepository;
import com.dkds.authserver.authorization.UserVerification;
import com.dkds.authserver.authorization.UserVerificationRepository;
import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/// Phase 4 tests: per-organization MFA policy enforcement.
///
/// Per DESIGN.md/PLAN.md, `OrgPolicyRequiredAuthoritiesRepository` computes
/// the strictest-wins MFA interval across a user's active memberships and
/// requires FACTOR_OTT only when `user_verification.verified_at` has aged
/// past it. These tests build their own fixtures rather than relying on
/// DataInitializer's seed data, since that data is verified "just now" at
/// seed time and can't discriminate a daily-stale-but-30-day-fresh scenario.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 4: Per-Organization MFA Policy")
class Phase4AuthorizationPolicyTests {

    @Autowired
    private OrgPolicyRequiredAuthoritiesRepository orgPolicyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrgSecurityPolicyRepository orgSecurityPolicyRepository;

    @Autowired
    private UserVerificationRepository userVerificationRepository;

    private AppUser createUser(String username) {
        var user = AppUser.builder()
                .username(username)
                .passwordHash("{noop}unused")
                .enabled(true)
                .failedAttempts(0)
                .build();
        return userRepository.save(user);
    }

    private Organization createOrg(String code, MfaMode mfaMode) {
        var org = organizationRepository.save(Organization.builder()
                .code(code)
                .name(code)
                .active(true)
                .build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId())
                .mfaMode(mfaMode)
                .ipRestrictionEnabled(false)
                .build());
        return org;
    }

    private void addActiveMembership(AppUser user, Organization org) {
        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .active(true)
                .build());
    }

    private void verifyEmailAt(AppUser user, Instant verifiedAt) {
        userVerificationRepository.save(UserVerification.builder()
                .user(user)
                .method(UserVerification.METHOD_EMAIL)
                .verifiedAt(verifiedAt)
                .build());
    }

    @Test
    @DisplayName("1. Strictest-wins: NEVER / EVERY_30_DAYS / DAILY memberships require OTT under the daily interval")
    void strictestIntervalWinsAcrossMemberships() {
        var user = createUser("phase4-strictest");
        addActiveMembership(user, createOrg("P4-NEVER", MfaMode.NEVER));
        addActiveMembership(user, createOrg("P4-30D", MfaMode.EVERY_30_DAYS));
        addActiveMembership(user, createOrg("P4-DAILY", MfaMode.DAILY));

        // Stale for the daily interval (>1 day) but still fresh for the
        // 30-day one. If the weakest membership (NEVER/30-day) governed
        // instead of the strictest (daily), this would incorrectly permit.
        verifyEmailAt(user, Instant.now().minus(2, ChronoUnit.DAYS));

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-strictest"))
                .containsExactly(FactorGrantedAuthority.OTT_AUTHORITY);
    }

    @Test
    @DisplayName("2. Interval satisfied: verified two hours ago under daily requires no OTT")
    void satisfiedIntervalRequiresNoOtt() {
        var user = createUser("phase4-satisfied");
        addActiveMembership(user, createOrg("P4-DAILY-2", MfaMode.DAILY));
        verifyEmailAt(user, Instant.now().minus(2, ChronoUnit.HOURS));

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-satisfied")).isEmpty();
    }

    @Test
    @DisplayName("3. Fail closed: a data problem while computing the requirement still requires OTT")
    void failsClosedOnError() {
        var user = createUser("phase4-failclosed");
        var org = organizationRepository.save(Organization.builder()
                .code("P4-NOPOLICY")
                .name("P4-NOPOLICY")
                .active(true)
                .build());
        // Deliberately no OrgSecurityPolicy row for this org — an active
        // membership pointing at unreadable/missing policy data must fail
        // closed rather than silently skip the requirement.
        addActiveMembership(user, org);

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-failclosed"))
                .containsExactly(FactorGrantedAuthority.OTT_AUTHORITY);
    }

    @Test
    @DisplayName("4. A PLATFORM_ADMIN with a membership in a daily org still requires MFA")
    void adminMembershipStillGatesOnPolicy() {
        var admin = createUser("phase4-admin");
        addActiveMembership(admin, createOrg("P4-ADMIN-DAILY", MfaMode.DAILY));
        // No verification at all: never verified.

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-admin"))
                .containsExactly(FactorGrantedAuthority.OTT_AUTHORITY);
    }

    @Test
    @DisplayName("No active memberships require MFA: no requirement at all")
    void noQualifyingMembershipRequiresNothing() {
        var user = createUser("phase4-never");
        addActiveMembership(user, createOrg("P4-NEVER-ONLY", MfaMode.NEVER));

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-never")).isEmpty();
    }

    @Test
    @DisplayName("Inactive membership does not contribute to the requirement")
    void inactiveMembershipIsIgnored() {
        var user = createUser("phase4-inactive");
        var org = createOrg("P4-INACTIVE-DAILY", MfaMode.DAILY);
        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .active(false)
                .build());

        assertThat(orgPolicyRepository.findRequiredAuthorities("phase4-inactive")).isEmpty();
    }

    @Test
    @DisplayName("Unknown username (notably the anonymous principal's name) requires nothing extra, "
            + "rather than failing closed: this manager runs before the base authenticated() check "
            + "in the composed AuthorizationManager, so it sees every request including anonymous ones")
    void unknownUsernameRequiresNothing() {
        assertThat(orgPolicyRepository.findRequiredAuthorities("anonymousUser")).isEmpty();
        assertThat(orgPolicyRepository.findRequiredAuthorities("no-such-user-at-all")).isEmpty();
    }
}
