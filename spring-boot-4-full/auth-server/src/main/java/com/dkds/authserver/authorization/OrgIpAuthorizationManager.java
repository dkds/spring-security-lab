package com.dkds.authserver.authorization;

import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.OrgIpRangeRepository;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.user.UserRepository;
import com.dkds.authserver.user.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/// Denies requests from outside an org's allowed IP ranges.
///
/// Per DESIGN.md:
/// - PLATFORM_ADMIN is exempt. Return permit immediately.
/// - Otherwise every restricted range from the user's active memberships must
///   permit the address: for each active-membership org with
///   ip_restriction_enabled=true, the client address must fall inside at
///   least one of that org's CIDR blocks. A single restricted org whose
///   ranges don't cover the address denies the whole request — this is not
///   "any org's ranges will do."
/// - IP is not a factor. This is a plain grant/deny gate, not a
///   FactorGrantedAuthority, and is composed into the same
///   AuthorizationManagerFactory.additionalAuthorization as the OTT policy
///   manager via AuthorizationManagers.allOf(orgPolicy, ipPolicy) — see
///   AuthorizationPolicyConfig.
/// - Reads request.getRemoteAddr() only — never parses X-Forwarded-For
///   itself. Whether that value reflects a real proxied client depends on
///   ForwardedHeaderFilter registration, which is deliberately NOT enabled by
///   default (see SecurityConfig) since this deployment has no trusted
///   reverse proxy in front of it; enabling it unconditionally would let any
///   direct caller spoof X-Forwarded-For and bypass this check entirely.
///
/// Deliberately depends only on `organization`, `user`, and framework types,
/// never on `com.dkds.authserver.security` (DESIGN.md: nothing may depend on
/// `security`) — ROLE_PLATFORM_ADMIN is reconstructed locally from
/// UserRole.PLATFORM_ADMIN rather than importing SecurityConstants.
@Component
@RequiredArgsConstructor
@Slf4j
public class OrgIpAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String ROLE_PLATFORM_ADMIN = "ROLE_" + UserRole.PLATFORM_ADMIN;

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final OrgSecurityPolicyRepository orgSecurityPolicyRepository;
    private final OrgIpRangeRepository orgIpRangeRepository;

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                          RequestAuthorizationContext context) {
        var principal = authentication.get();
        try {
            return computeDecision(principal, context.getRequest());
        } catch (Exception ex) {
            log.warn("Failed to evaluate IP restriction for '{}'; failing closed",
                    principal != null ? principal.getName() : "?", ex);
            return new AuthorizationDecision(false);
        }
    }

    private AuthorizationDecision computeDecision(Authentication authentication, HttpServletRequest request) {
        if (authentication == null) {
            // Nothing additional to require here; the base authenticated()
            // check in the composed manager is what denies unauthenticated
            // requests. Same reasoning as OrgPolicyRequiredAuthoritiesRepository.
            return new AuthorizationDecision(true);
        }
        if (isPlatformAdmin(authentication)) {
            return new AuthorizationDecision(true);
        }
        var userOpt = userRepository.findByUsername(authentication.getName());
        if (userOpt.isEmpty()) {
            // Notably the anonymous principal's name, since this manager
            // runs before the base authenticated() check for every request.
            return new AuthorizationDecision(true);
        }
        var user = userOpt.get();
        var memberships = membershipRepository.findByUserIdAndActiveTrue(user.getId());
        var clientAddress = request.getRemoteAddr();

        for (var membership : memberships) {
            var orgId = membership.getOrganization().getId();
            var policy = orgSecurityPolicyRepository.findById(orgId)
                    .orElseThrow(() -> new IllegalStateException("No security policy for org " + orgId));
            if (!policy.getIpRestrictionEnabled()) {
                continue;
            }
            var ranges = orgIpRangeRepository.findByOrganizationId(orgId);
            var permittedByThisOrg = ranges.stream()
                    .anyMatch(range -> new IpAddressMatcher(range.getCidr()).matches(clientAddress));
            if (!permittedByThisOrg) {
                return new AuthorizationDecision(false);
            }
        }
        return new AuthorizationDecision(true);
    }

    private static boolean isPlatformAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_PLATFORM_ADMIN.equals(authority.getAuthority()));
    }
}
