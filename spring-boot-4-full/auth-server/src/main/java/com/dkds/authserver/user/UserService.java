package com.dkds.authserver.user;

import com.dkds.authserver.organization.MembershipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/// Service for user-related business logic.
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;

    /// Check if a user has at least one active membership.
    /// Invariant: every user who can log in holds at least one active membership.
    public boolean hasActiveMembership(String username) {
        var user = userRepository.findByUsername(username);
        if (user.isEmpty()) {
            return false;
        }
        var activeMemberships = membershipRepository.findByUserIdAndActiveTrue(user.get().getId());
        return !activeMemberships.isEmpty();
    }

    /// Get user by username.
    public AppUser getUserByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }
}
