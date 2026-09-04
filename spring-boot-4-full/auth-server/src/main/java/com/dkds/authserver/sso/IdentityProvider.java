package com.dkds.authserver.sso;

import jakarta.persistence.*;
import lombok.*;

/// A SAML2 IdP registration for one organization. DatabaseRelyingPartyRegistrationRepository
/// turns each active row into a Spring Security RelyingPartyRegistration.
///
/// Per DESIGN.md's schema: id, registration_id, org_id, entity_id, sso_url,
/// certificate, active.
@Entity
@Table(name = "identity_provider")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /// Matches Spring Security's {registrationId} URL template variable
    /// (/login/saml2/sso/{registrationId}, /saml2/authenticate/{registrationId}).
    @Column(name = "registration_id", nullable = false, unique = true)
    private String registrationId;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /// The IdP's own entity ID (Keycloak: the realm issuer URL).
    @Column(name = "entity_id", nullable = false)
    private String entityId;

    /// The IdP's SingleSignOnService location (HTTP-Redirect binding).
    @Column(name = "sso_url", nullable = false)
    private String ssoUrl;

    /// PEM-encoded X.509 certificate used to verify the IdP's signed responses/assertions.
    @Column(nullable = false, length = 4000)
    private String certificate;

    @Column(nullable = false)
    private Boolean active;
}
