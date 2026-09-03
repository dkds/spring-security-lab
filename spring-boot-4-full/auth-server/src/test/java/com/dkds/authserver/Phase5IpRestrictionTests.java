package com.dkds.authserver;

import com.dkds.authserver.organization.Membership;
import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.organization.MfaMode;
import com.dkds.authserver.organization.Organization;
import com.dkds.authserver.organization.OrganizationRepository;
import com.dkds.authserver.organization.OrgIpRange;
import com.dkds.authserver.organization.OrgIpRangeRepository;
import com.dkds.authserver.organization.OrgSecurityPolicy;
import com.dkds.authserver.organization.OrgSecurityPolicyRepository;
import com.dkds.authserver.user.AppUser;
import com.dkds.authserver.user.UserRepository;
import com.dkds.authserver.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Phase 5 regression tests: OrgIpAuthorizationManager, per DESIGN.md and the
/// four tests PLAN.md requires for this phase.
///
/// Every scenario uses its own org (one CIDR block, 203.0.113.0/24 —
/// TEST-NET-3, RFC 5737) so tests can't interfere with each other under
/// @Transactional rollback. 198.51.100.0/24 (TEST-NET-2) stands in for an
/// address outside that block.
///
/// Hits /login-success (Chain 3, plain anyRequest().authenticated(), no MFA
/// gate involved since these orgs are all mfaMode=NEVER) for the in-range /
/// out-of-range / admin-exemption tests, and the real /oauth2/authorize
/// redirect path (Chain 1) for the spoofed-header test, per PLAN.md's "verify
/// on the redirect path, not only on an XHR."
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 5: IP restriction")
class Phase5IpRestrictionTests {

    private static final String PASSWORD = "testpass123";
    private static final String IN_RANGE_IP = "203.0.113.10";
    private static final String OUT_OF_RANGE_IP = "198.51.100.10";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Autowired
    private OrgSecurityPolicyRepository orgSecurityPolicyRepository;

    @Autowired
    private OrgIpRangeRepository orgIpRangeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Organization createIpRestrictedOrg(String code) {
        var org = organizationRepository.save(Organization.builder()
                .code(code)
                .name(code)
                .active(true)
                .build());
        orgSecurityPolicyRepository.save(OrgSecurityPolicy.builder()
                .orgId(org.getId())
                .mfaMode(MfaMode.NEVER)
                .ipRestrictionEnabled(true)
                .build());
        orgIpRangeRepository.save(OrgIpRange.builder()
                .organization(org)
                .cidr("203.0.113.0/24")
                .build());
        return org;
    }

    private AppUser createUserIn(Organization org, String username, UserRole role) {
        var user = userRepository.save(AppUser.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .enabled(true)
                .failedAttempts(0)
                .role(role)
                .build());
        membershipRepository.save(Membership.builder()
                .user(user)
                .organization(org)
                .active(true)
                .build());
        return user;
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", PASSWORD)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    @DisplayName("In-range address is permitted")
    void inRangeAddressPermitted() throws Exception {
        var org = createIpRestrictedOrg("P5-IN-RANGE");
        var user = createUserIn(org, "p5-in-range@test", UserRole.MEMBER);
        var session = login(user.getUsername());

        mockMvc.perform(get("/login-success")
                        .session(session)
                        .with(req -> {
                            req.setRemoteAddr(IN_RANGE_IP);
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Out-of-range address is denied")
    void outOfRangeAddressDenied() throws Exception {
        var org = createIpRestrictedOrg("P5-OUT-RANGE");
        var user = createUserIn(org, "p5-out-range@test", UserRole.MEMBER);
        var session = login(user.getUsername());

        mockMvc.perform(get("/login-success")
                        .session(session)
                        .with(req -> {
                            req.setRemoteAddr(OUT_OF_RANGE_IP);
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PLATFORM_ADMIN is exempt from IP restriction, even from an out-of-range address")
    void platformAdminExemptFromOutOfRangeAddress() throws Exception {
        var org = createIpRestrictedOrg("P5-ADMIN-EXEMPT");
        var admin = createUserIn(org, "p5-admin@test", UserRole.PLATFORM_ADMIN);
        var session = login(admin.getUsername());

        mockMvc.perform(get("/login-success")
                        .session(session)
                        .with(req -> {
                            req.setRemoteAddr(OUT_OF_RANGE_IP);
                            return req;
                        }))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("A non-admin is still denied from that same out-of-range address — the exemption is role-scoped")
    void nonAdminDeniedFromSameOutOfRangeAddress() throws Exception {
        var org = createIpRestrictedOrg("P5-NON-ADMIN");
        var member = createUserIn(org, "p5-member@test", UserRole.MEMBER);
        var session = login(member.getUsername());

        mockMvc.perform(get("/login-success")
                        .session(session)
                        .with(req -> {
                            req.setRemoteAddr(OUT_OF_RANGE_IP);
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("A spoofed X-Forwarded-For does not grant access, on the actual /oauth2/authorize redirect path")
    void spoofedForwardedForNotTrustedOnAuthorizeRedirect() throws Exception {
        var org = createIpRestrictedOrg("P5-SPOOF");
        var member = createUserIn(org, "p5-spoof@test", UserRole.MEMBER);
        var session = login(member.getUsername());

        var authorizeRequest = "/oauth2/authorize?client_id=spa-client"
                + "&redirect_uri=http://localhost:5173/callback"
                + "&response_type=code"
                + "&scope=openid"
                + "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
                + "&code_challenge_method=S256";

        // The real remote address is out of range; the header claims an
        // in-range one. server.forward-headers-strategy is "none" (see
        // SecurityConfig), so nothing rewrites getRemoteAddr() from this
        // header — it must have zero effect.
        mockMvc.perform(get(authorizeRequest)
                        .session(session)
                        .header("X-Forwarded-For", IN_RANGE_IP)
                        .with(req -> {
                            req.setRemoteAddr(OUT_OF_RANGE_IP);
                            return req;
                        }))
                .andExpect(status().isForbidden());
    }
}
