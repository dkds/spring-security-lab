package com.dkds.authserver;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.dkds.authserver.sso.SamlUserAuthoritiesConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Subject;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/// An IdP we trust asserting an identity we've never provisioned is a
/// distinct, security-relevant signal — worth being able to find in logs on
/// its own, not folded into ordinary authentication-failure noise (could
/// mean a misconfigured IdP, a stale NameID mapping, or someone probing).
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Phase 10: unrecognized SAML identity is logged distinctly")
class Phase10UnrecognizedSamlIdentityLoggingTests {

    @Autowired
    private SamlUserAuthoritiesConverter converter;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(SamlUserAuthoritiesConverter.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(SamlUserAuthoritiesConverter.class)).detachAppender(logAppender);
    }

    @Test
    @DisplayName("An unrecognized NameID is logged at WARN with the issuer and the NameID, before the exception is thrown")
    void unrecognizedIdentityIsLoggedDistinctly() {
        assertThatThrownBy(() -> converter.convert(assertionFor(
                "http://idp.test.invalid/realms/untrusted-lab", "phase10-unrecognized@test")))
                .isInstanceOf(UsernameNotFoundException.class);

        assertThat(logAppender.list)
                .as("exactly one distinct warning for the unrecognized identity, not silent")
                .hasSize(1);
        var event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("phase10-unrecognized@test")
                .contains("http://idp.test.invalid/realms/untrusted-lab");
    }

    private static Assertion assertionFor(String issuerValue, String nameId) {
        NameID nameID = mock(NameID.class);
        when(nameID.getValue()).thenReturn(nameId);
        Subject subject = mock(Subject.class);
        when(subject.getNameID()).thenReturn(nameID);
        Issuer issuer = mock(Issuer.class);
        when(issuer.getValue()).thenReturn(issuerValue);
        Assertion assertion = mock(Assertion.class);
        when(assertion.getSubject()).thenReturn(subject);
        when(assertion.getIssuer()).thenReturn(issuer);
        when(assertion.getAuthnStatements()).thenReturn(List.of());
        return assertion;
    }
}
