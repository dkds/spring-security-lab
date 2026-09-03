package com.dkds.authserver.user;

/// Global role for a user, independent of organization membership.
///
/// Per DESIGN.md: "Roles: MEMBER, PLATFORM_ADMIN." Maps directly onto
/// ROLE_MEMBER / ROLE_PLATFORM_ADMIN granted authorities in
/// AppUserDetailsService.
public enum UserRole {
    MEMBER,
    PLATFORM_ADMIN
}
