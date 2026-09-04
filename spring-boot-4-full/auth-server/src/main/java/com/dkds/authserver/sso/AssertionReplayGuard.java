package com.dkds.authserver.sso;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.saml2.core.Saml2Error;
import org.springframework.security.saml2.core.Saml2ErrorCodes;
import org.springframework.security.saml2.core.Saml2ResponseValidatorResult;
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;

/// Rejects a SAML2 assertion whose ID has already been presented once.
///
/// Per DESIGN.md: "AssertionReplayGuard: persist seen assertion IDs past the
/// validity window. Unsolicited assertions have no InResponseTo, so this is
/// the replay defence." Verified directly against
/// BaseOpenSamlAuthenticationProvider.validateInResponseTo: when a response's
/// InResponseTo is blank (the unsolicited/IdP-initiated case), that check
/// returns success() immediately with no correlation attempted — Spring's
/// built-in validators genuinely provide no replay protection for that case,
/// which is exactly the case Phase 8 starts accepting.
///
/// Delegates to OpenSaml5AuthenticationProvider.AssertionValidator.withDefaults()
/// first (signature, conditions, audience, subject confirmation — unchanged),
/// and only checks for replay if that passes.
///
/// The actual replay check lives in the separate isReplay(String) method
/// rather than inline in convert(...) — deliberately: AssertionToken's
/// constructors are package-private to spring-security-saml2's own package,
/// so it can't be constructed (or usefully mocked, since a bare mock would
/// still need to satisfy the real cryptographic default validator first) from
/// this module's tests. isReplay(String) is the actual seam
/// Phase8AssertionReplayGuardTests exercises directly, with no OpenSAML types
/// involved at all; convert(...) itself — the full wiring, real crypto
/// included — is verified live against a real Keycloak instance instead,
/// same precedent as the rest of this codebase's crypto-heavy paths (see
/// AGENTS.md Known Gaps).
@Component
@RequiredArgsConstructor
public class AssertionReplayGuard
        implements Converter<OpenSaml5AuthenticationProvider.AssertionToken, Saml2ResponseValidatorResult> {

    private final OpenSaml5AuthenticationProvider.AssertionValidator defaultValidator =
            OpenSaml5AuthenticationProvider.AssertionValidator.withDefaults();

    private final SeenSamlAssertionRepository seenSamlAssertionRepository;

    @Override
    public Saml2ResponseValidatorResult convert(OpenSaml5AuthenticationProvider.AssertionToken token) {
        Saml2ResponseValidatorResult result = defaultValidator.convert(token);
        if (result.hasErrors()) {
            return result;
        }

        String assertionId = token.getAssertion().getID();
        if (isReplay(assertionId)) {
            return result.concat(new Saml2Error(Saml2ErrorCodes.INVALID_ASSERTION,
                    "Assertion [" + assertionId + "] has already been used"));
        }
        return result;
    }

    /// Records assertionId as seen; returns whether it had ALREADY been seen
    /// before this call. Uses the seen_saml_assertion table's primary key as
    /// the actual enforcement — saveAndFlush() rather than a separate
    /// exists-then-insert check, so two concurrent presentations of the same
    /// assertion can't both slip past a check-then-act race; the second
    /// INSERT genuinely fails on the DB's own uniqueness constraint.
    public boolean isReplay(String assertionId) {
        try {
            seenSamlAssertionRepository.saveAndFlush(new SeenSamlAssertion(assertionId, Instant.now()));
            return false;
        } catch (DataIntegrityViolationException ex) {
            return true;
        }
    }
}
