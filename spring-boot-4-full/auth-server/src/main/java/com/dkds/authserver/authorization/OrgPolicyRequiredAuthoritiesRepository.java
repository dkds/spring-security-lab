package com.dkds.authserver.authorization;

import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.RequiredAuthoritiesRepository;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/// Computes the additional authorities a user must hold to be considered
/// authorized, based on their active organization memberships.
///
/// Per DESIGN.md:
/// - Scope is the user's own active memberships, for every role including
///   PLATFORM_ADMIN.
/// - MFA interval = minimum across those memberships. EVERY_LOGIN=0, DAILY=1d,
///   EVERY_30_DAYS=30d, NEVER=absent.
/// - Require FACTOR_OTT only when user_verification.verified_at is older than
///   that interval.
/// - On any exception, return the full requirement set. Fail closed.
///
/// Deliberately depends only on `organization` and framework types, never on
/// `com.dkds.authserver.security` (DESIGN.md: nothing may depend on
/// `security`).
@Component
@RequiredArgsConstructor
@Slf4j
public class OrgPolicyRequiredAuthoritiesRepository implements RequiredAuthoritiesRepository {

    private static final List<String> FULL_REQUIREMENT_SET = List.of(FactorGrantedAuthority.OTT_AUTHORITY);

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final OrgSecurityPolicyRepository orgSecurityPolicyRepository;
    private final UserVerificationRepository userVerificationRepository;
    private final Clock clock;

    @Override
    public List<String> findRequiredAuthorities(String username) {
        try {
            return computeRequiredAuthorities(username);
        } catch (Exception ex) {
            log.warn("Failed to compute MFA requirement for '{}'; failing closed", username, ex);
            return FULL_REQUIREMENT_SET;
        }
    }

    private List<String> computeRequiredAuthorities(String username) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            // Not a real account — notably, this is what the anonymous
            // principal's name resolves to. This manager runs before the
            // base authenticated() check in the composed AuthorizationManager
            // (DefaultAuthorizationManagerFactory.withAdditionalAuthorization
            // puts it first), so it sees every request, including
            // unauthenticated ones. Nothing additional to require here; the
            // base check is what denies those.
            return List.of();
        }
        var user = userOpt.get();
        var memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());

        Duration interval = null;
        for (var membership : memberships) {
            var orgId = membership.getOrganization().getId();
            var policy = orgSecurityPolicyRepository.findById(orgId)
                    .orElseThrow(() -> new IllegalStateException("No security policy for org " + orgId));
            var candidate = intervalFor(policy.getMfaMode());
            if (candidate == null) {
                continue;
            }
            if (interval == null || candidate.compareTo(interval) < 0) {
                interval = candidate;
            }
        }

        if (interval == null) {
            return List.of();
        }

        var verifiedAt = userVerificationRepository
                .findByUserIdAndMethod(user.getId(), UserVerification.METHOD_EMAIL)
                .map(UserVerification::getVerifiedAt)
                .orElse(null);

        var requiredSince = clock.instant().minus(interval);
        if (verifiedAt == null || verifiedAt.isBefore(requiredSince)) {
            return FULL_REQUIREMENT_SET;
        }
        return List.of();
    }

    /// Null means the mode imposes no MFA interval (NEVER).
    private static Duration intervalFor(MfaMode mode) {
        return switch (mode) {
            case NEVER -> null;
            case EVERY_LOGIN -> Duration.ZERO;
            case DAILY -> Duration.ofDays(1);
            case EVERY_30_DAYS -> Duration.ofDays(30);
        };
    }
}
