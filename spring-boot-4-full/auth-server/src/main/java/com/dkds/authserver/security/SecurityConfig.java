package com.dkds.authserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/// Global security configuration beans.
/// Filter chains are defined in the SecurityChains class.
///
/// Note on IP restriction (Phase 5) and `server.forward-headers-strategy`:
/// DESIGN.md asks to register Spring's `ForwardedHeaderFilter` "with trusted
/// proxies" so `OrgIpAuthorizationManager` sees the real client address
/// behind a reverse proxy. That filter has no proxy allowlist of its own,
/// though — once active it unconditionally trusts whatever `X-Forwarded-For`
/// a caller sends, with no way to scope it to specific upstream peers. This
/// docker-compose stack has no reverse proxy in front of `auth-server`
/// (exposed directly), so `server.forward-headers-strategy` is deliberately
/// left at Boot's default (`none`) everywhere in this repo — enabling it
/// unconditionally would let any direct caller spoof `X-Forwarded-For` and
/// walk straight past IP restriction. `OrgIpAuthorizationManager` reads
/// `request.getRemoteAddr()` as-is, never parsing the header itself, so this
/// is exactly what PLAN.md's "spoofed X-Forwarded-For not trusted" test
/// verifies. The day this app is actually deployed behind a real reverse
/// proxy that scrubs/overwrites client-supplied `X-Forwarded-For` before
/// forwarding, set `server.forward-headers-strategy: framework` in that
/// environment's profile — that's what registers `ForwardedHeaderFilter`.
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
