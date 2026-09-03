package com.dkds.authserver.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/// Loads user details for authentication.
///
/// Per DESIGN.md "Terminal rejections":
/// Disabled, locked, no active membership, password expired are NOT factors and
/// must not route through the gate. They map onto UserDetails flags so that
/// DaoAuthenticationProvider rejects at primary authentication:
///
/// ```
/// .disabled(!user.isEnabled())
/// .accountLocked(user.isLocked())
/// .accountExpired(!memberships.hasActiveMembership(username))
/// .credentialsExpired(user.isPasswordExpired())
/// ```
///
/// Invariant: every user who can log in holds at least one active membership.
@Service
@RequiredArgsConstructor
@Slf4j
public class AppUserDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userService.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }

        // Terminal rejections mapped onto UserDetails flags.
        // DaoAuthenticationProvider throws the appropriate exception
        // (DisabledException, LockedException, AccountExpiredException,
        // CredentialsExpiredException) based on these flags.
        return User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.getEnabled())
                .accountLocked(user.isLocked())
                .accountExpired(!userService.hasActiveMembership(username))
                .credentialsExpired(user.isPasswordExpired())
                .build();
    }
}
