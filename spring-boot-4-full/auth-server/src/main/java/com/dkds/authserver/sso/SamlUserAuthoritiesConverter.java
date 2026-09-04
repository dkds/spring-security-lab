package com.dkds.authserver.sso;

import com.dkds.authserver.user.UserService;
import lombok.RequiredArgsConstructor;
import org.opensaml.saml.saml2.core.Assertion;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/// Derives the assertion's ROLE_* authorities and, per DESIGN.md, applies the
/// SAME terminal-rejection checks AppUserDetailsService applies on the
/// password path (disabled/locked/no active membership/password expired) —
/// an assertion only proves the IdP authenticated the user, not that the user
/// is enabled or assigned here. OpenSaml5AuthenticationProvider never
/// consults UserDetailsService, so nothing else in the SAML path would catch
/// these otherwise.
///
/// Plugged in as the ResponseAuthenticationConverter's
/// grantedAuthoritiesConverter (see Saml2Configurer) rather than a full
/// replacement Converter<ResponseToken, Saml2Authentication>: that hook
/// already receives the validated Assertion and is called during
/// conversion, so throwing here surfaces as an AuthenticationException the
/// same way a DaoAuthenticationProvider rejection would. The delegate also
/// unconditionally grants FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY —
/// nothing to add for that here.
@Component
@RequiredArgsConstructor
public class SamlUserAuthoritiesConverter implements Converter<Assertion, Collection<GrantedAuthority>> {

    private final UserService userService;

    @Override
    public Collection<GrantedAuthority> convert(Assertion assertion) {
        String username = extractUsername(assertion);
        var user = userService.getUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        if (!user.getEnabled()) {
            throw new DisabledException("User disabled: " + username);
        }
        if (user.isLocked()) {
            throw new LockedException("User locked: " + username);
        }
        if (!userService.hasActiveMembership(username)) {
            throw new AccountExpiredException("No active membership: " + username);
        }
        if (user.isPasswordExpired()) {
            throw new CredentialsExpiredException("Password expired: " + username);
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    private static String extractUsername(Assertion assertion) {
        var subject = assertion.getSubject();
        if (subject == null || subject.getNameID() == null || subject.getNameID().getValue() == null) {
            throw new Saml2AuthenticationException(
                    Saml2Error.subjectNotFound("Assertion [" + assertion.getID() + "] is missing a subject"));
        }
        return subject.getNameID().getValue();
    }
}
