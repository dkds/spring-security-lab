package com.dkds.authserver.sso;

import com.dkds.authserver.organization.MembershipRepository;
import com.dkds.authserver.sso.dto.SsoOption;
import com.dkds.authserver.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

/// SSO discovery/selection flow: the user types their username, this looks
/// up which of their active memberships have an active identity_provider
/// configured, and lists those as SAML sign-in options — always the picker
/// screen, even for a single match (no auto-redirect).
///
/// Both endpoints are necessarily reachable pre-authentication (permitAll,
/// see SecurityChains) — this IS how a user gets authenticated. That makes
/// this a username-enumeration surface if not handled carefully: an unknown
/// username and a known username with no SSO-eligible org membership must
/// render the exact same generic result. See findSsoOptions.
@Controller
@RequiredArgsConstructor
public class SsoDiscoveryController {

    public static final String SSO_DISCOVER_URL = "/sso/discover";

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final IdentityProviderRepository identityProviderRepository;

    @GetMapping(SSO_DISCOVER_URL)
    public String discoverPage() {
        return "sso-discover";
    }

    @PostMapping(SSO_DISCOVER_URL)
    @Transactional(readOnly = true)
    public String discover(@RequestParam String username, Model model) {
        model.addAttribute("username", username);
        model.addAttribute("options", findSsoOptions(username));
        return "sso-select-org";
    }

    /// Deliberately returns the SAME empty list whether the username doesn't
    /// exist at all or exists but has no active membership in an
    /// SSO-configured org — the rendered page must not let an anonymous
    /// caller distinguish "no such account" from "this account has no SSO".
    private List<SsoOption> findSsoOptions(String username) {
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return List.of();
        }
        var memberships = membershipRepository.findByUserIdAndActiveTrue(userOpt.get().getId());
        if (memberships.isEmpty()) {
            return List.of();
        }
        var orgNameById = memberships.stream()
                .collect(Collectors.toMap(m -> m.getOrganization().getId(), m -> m.getOrganization().getName()));

        return identityProviderRepository.findByOrgIdInAndActiveTrue(orgNameById.keySet()).stream()
                .map(idp -> new SsoOption(idp.getRegistrationId(), orgNameById.get(idp.getOrgId())))
                .toList();
    }
}
