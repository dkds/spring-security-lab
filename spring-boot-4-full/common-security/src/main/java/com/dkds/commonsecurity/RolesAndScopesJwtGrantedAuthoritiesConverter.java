package com.dkds.commonsecurity;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.LinkedHashSet;

/// Derives granted authorities from a validated access token's standard
/// {@code scope} claim (as {@code SCOPE_*}, Spring's own default behavior)
/// AND its {@code roles} claim (as {@code ROLE_*}) — the realistic claim set
/// the authorization server's {@code AccessTokenCustomizer} writes onto every
/// access token it issues.
///
/// {@link #ROLES_CLAIM_NAME} and {@link #ROLE_AUTHORITY_PREFIX} are the
/// contract between the token issuer (auth-server's
/// {@code AccessTokenCustomizer}) and every consumer of tokens it mints
/// (auth-server's own Chain 2, and every resource-server via
/// {@link ResourceServerSecurityConfig}) — reference these constants on both
/// ends rather than repeating the literal claim name, so a rename fails to
/// compile instead of silently breaking authorization at runtime.
public final class RolesAndScopesJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLES_CLAIM_NAME = "roles";
    public static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

    private final JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
    private final JwtGrantedAuthoritiesConverter roleConverter = new JwtGrantedAuthoritiesConverter();

    public RolesAndScopesJwtGrantedAuthoritiesConverter() {
        roleConverter.setAuthoritiesClaimName(ROLES_CLAIM_NAME);
        roleConverter.setAuthorityPrefix(ROLE_AUTHORITY_PREFIX);
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        var authorities = new LinkedHashSet<GrantedAuthority>();
        authorities.addAll(scopeConverter.convert(jwt));
        authorities.addAll(roleConverter.convert(jwt));
        return authorities;
    }
}
