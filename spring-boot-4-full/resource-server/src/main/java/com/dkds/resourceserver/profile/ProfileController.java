package com.dkds.resourceserver.profile;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Demo endpoint proving the realistic access-token claim set (Phase 6)
/// actually reaches a resource server: echoes back the validated JWT's
/// subject and roles claim, plus the authorities RolesAndScopesJwtGrantedAuthoritiesConverter
/// derived from it (SCOPE_* from the standard "scope" claim, ROLE_* from
/// "roles" — see common-security).
@RestController
public class ProfileController {

    @GetMapping("/api/profile")
    public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        List<String> authorities = authentication.getAuthorities().stream()
                .map(Object::toString)
                .toList();
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new ProfileResponse(jwt.getSubject(), roles != null ? roles : List.of(), authorities);
    }

    public record ProfileResponse(String subject, List<String> roles, List<String> authorities) {
    }
}
