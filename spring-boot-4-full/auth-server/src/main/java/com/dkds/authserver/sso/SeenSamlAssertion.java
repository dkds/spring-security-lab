package com.dkds.authserver.sso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/// A SAML2 assertion ID already presented once, tracked so AssertionReplayGuard
/// can reject a second presentation. Per DESIGN.md: "persist seen assertion
/// IDs past the validity window" — deliberately no automatic cleanup here;
/// rows are small (one ID + a timestamp) and this is a lab/POC, not a
/// production deployment that needs to bound this table's growth.
///
/// Implements Persistable<String> and always reports isNew()=true.
/// assertionId is an application-assigned @Id (not @GeneratedValue), so
/// Spring Data JPA's default isNew() strategy (null-ID check) would see a
/// non-null ID on every save() call and route it through
/// entityManager.merge() instead of persist() — merge silently UPDATES an
/// existing row on a duplicate ID rather than failing, which would make
/// AssertionReplayGuard's whole replay check a no-op. Forcing isNew()=true
/// makes save() always go through persist(), so a genuine duplicate hits the
/// table's own primary-key constraint and throws, as intended. Safe here
/// specifically because this entity is write-only — nothing in this codebase
/// ever loads/updates an existing row.
@Entity
@Table(name = "seen_saml_assertion")
@Getter
public class SeenSamlAssertion implements Persistable<String> {

    @Id
    @Column(name = "assertion_id")
    private String assertionId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;

    protected SeenSamlAssertion() {
    }

    public SeenSamlAssertion(String assertionId, Instant seenAt) {
        this.assertionId = assertionId;
        this.seenAt = seenAt;
    }

    @Override
    public String getId() {
        return assertionId;
    }

    @Override
    public boolean isNew() {
        return true;
    }
}
