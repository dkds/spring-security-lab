package com.dkds.authserver.sso;

import com.dkds.authserver.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/// Derives the assertion's ROLE_* (and, per Phase 10, FACTOR_IDP_MFA)
/// authorities and, per DESIGN.md, applies the SAME terminal-rejection
/// checks AppUserDetailsService applies on the password path
/// (disabled/locked/no active membership/password expired) — an assertion
/// only proves the IdP authenticated the user, not that the user is enabled
/// or assigned here. OpenSaml5AuthenticationProvider never consults
/// UserDetailsService, so nothing else in the SAML path would catch these
/// otherwise.
///
/// Plugged in as the ResponseAuthenticationConverter's
/// grantedAuthoritiesConverter (see Saml2Configurer) rather than a full
/// replacement Converter<ResponseToken, Saml2Authentication>: that hook
/// already receives the validated Assertion and is called during
/// conversion, so throwing here surfaces as an AuthenticationException the
/// same way a DaoAuthenticationProvider rejection would. The delegate also
/// unconditionally grants FactorGrantedAuthority.SAML_RESPONSE_AUTHORITY —
/// nothing to add for that here.
///
/// FACTOR_IDP_MFA (Phase 10): granted when the assertion's own
/// AuthnStatement/AuthnContext/AuthnContextClassRef names one of
/// MFA_AUTHN_CONTEXT_CLASS_REFS — i.e. the IdP itself is asserting it
/// performed multi-factor authentication for this login, not just that SAML
/// happened. There is no single standard URI every IdP uses for "this was
/// MFA" — the SAML V2.0 spec's own Authentication Context classes are the
/// most portable choice available (not vendor-specific), so that's what's
/// checked here rather than a single hardcoded vendor string. This factor is
/// what SamlOrPasswordOttAuthorizationManager's replacement — the org's own
/// dynamic OTT policy, now applied uniformly to SAML sessions too — treats
/// as equivalent to FACTOR_OTT via LoginRecordingListener writing
/// user_verification.verified_at for either one.
@Component
@RequiredArgsConstructor
@Slf4j
public class SamlUserAuthoritiesConverter implements Converter<Assertion, Collection<GrantedAuthority>> {

    /// The custom factor granted when the assertion's own AuthnContextClassRef
    /// indicates the IdP performed MFA — see assertsMfa(...) below. Public so
    /// LoginRecordingListener can treat it as equivalent to FACTOR_OTT
    /// without repeating the literal string.
    public static final String IDP_MFA_AUTHORITY = "FACTOR_IDP_MFA";

    /// SAML V2.0 Authentication Context class references that represent a
    /// multi-factor (or hardware-token-backed) authentication mechanism, per
    /// the OASIS SAML V2.0 Authentication Context spec. Deliberately the
    /// portable, spec-defined vocabulary rather than a specific IdP's own
    /// custom URI — a real deployment against a specific IdP may need to
    /// extend this set to match whatever AuthnContextClassRef that IdP
    /// actually emits for its own MFA flows.
    private static final Set<String> MFA_AUTHN_CONTEXT_CLASS_REFS = Set.of(
            "urn:oasis:names:tc:SAML:2.0:ac:classes:MobileTwoFactorContract",
            "urn:oasis:names:tc:SAML:2.0:ac:classes:MobileTwoFactorUnregistered",
            "urn:oasis:names:tc:SAML:2.0:ac:classes:SmartcardPKI",
            "urn:oasis:names:tc:SAML:2.0:ac:classes:TimeSyncToken");

    private final UserService userService;

    @Override
    public Collection<GrantedAuthority> convert(Assertion assertion) {
        String username = extractUsername(assertion);
        var user = userService.getUserByUsername(username);
        if (user == null) {
            // Distinct, greppable/alertable signal — an authentication
            // failure alone doesn't say WHICH kind. An IdP we trust
            // asserting an identity we've never provisioned is a stronger
            // signal than, say, a known-but-disabled user retrying (could
            // mean a misconfigured IdP, a stale NameID mapping, or someone
            // probing) — worth being able to find in logs on its own, not
            // folded into ordinary authentication-failure noise.
            log.warn("SAML assertion from issuer '{}' asserted an unrecognized identity: NameID='{}'",
                    issuerOf(assertion), username);
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

        var authorities = new ArrayList<GrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (assertsMfa(assertion)) {
            authorities.add(FactorGrantedAuthority.fromFactor("IDP_MFA"));
        }
        return authorities;
    }

    private static boolean assertsMfa(Assertion assertion) {
        for (AuthnStatement statement : assertion.getAuthnStatements()) {
            var authnContext = statement.getAuthnContext();
            if (authnContext == null || authnContext.getAuthnContextClassRef() == null) {
                continue;
            }
            String classRef = authnContext.getAuthnContextClassRef().getURI();
            if (MFA_AUTHN_CONTEXT_CLASS_REFS.contains(classRef)) {
                return true;
            }
        }
        return false;
    }

    private static String issuerOf(Assertion assertion) {
        var issuer = assertion.getIssuer();
        return issuer != null ? issuer.getValue() : "unknown";
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
