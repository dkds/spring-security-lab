package com.dkds.authserver.login;

import lombok.RequiredArgsConstructor;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/// Backs Spring Security's PersistentTokenBasedRememberMeServices with
/// RememberMeToken/RememberMeTokenRepository instead of the framework's own
/// JdbcTokenRepositoryImpl: that class issues a raw, unconditional
/// `create table persistent_logins (...)` on startup when
/// setCreateTableOnStartup(true) is set, with no "if not exists" guard — safe
/// only for a table that gets recreated from scratch every boot, which
/// contradicts this app's actual schema management (Hibernate
/// ddl-auto: update, same as every other table here) and would fail outright
/// on a second restart against a persistent database. Implementing the same
/// four-method PersistentTokenRepository interface against a normal JPA
/// entity keeps remember-me's storage consistent with everything else in
/// this codebase instead of introducing a second, incompatible schema
/// mechanism for one feature.
@Component
@RequiredArgsConstructor
public class JpaPersistentTokenRepository implements PersistentTokenRepository {

    private final RememberMeTokenRepository repository;

    @Override
    public void createNewToken(PersistentRememberMeToken token) {
        repository.save(RememberMeToken.builder()
                .series(token.getSeries())
                .username(token.getUsername())
                .tokenValue(token.getTokenValue())
                .lastUsed(token.getDate().toInstant())
                .build());
    }

    @Override
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        repository.findById(series).ifPresent(existing -> {
            existing.setTokenValue(tokenValue);
            existing.setLastUsed(lastUsed.toInstant());
            repository.save(existing);
        });
    }

    @Override
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        return repository.findById(seriesId)
                .map(t -> new PersistentRememberMeToken(t.getUsername(), t.getSeries(), t.getTokenValue(), Date.from(t.getLastUsed())))
                .orElse(null);
    }

    @Override
    @Transactional
    public void removeUserTokens(String username) {
        repository.deleteByUsername(username);
    }
}
