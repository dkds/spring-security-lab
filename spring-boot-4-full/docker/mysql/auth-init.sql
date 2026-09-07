-- Spring Security's One-Time Token schema is not created automatically.
-- JdbcOneTimeTokenService expects this table to exist.
CREATE TABLE IF NOT EXISTS one_time_tokens (
    token_value VARCHAR(255) NOT NULL PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL
);

-- Spring Authorization Server's JDBC schemas (oauth2_registered_client,
-- oauth2_authorization, oauth2_authorization_consent) ship inside the
-- spring-security-oauth2-authorization-server jar. Load them from the
-- application with a schema initializer rather than duplicating them
-- here, so they stay in step with the library version.

-- Secondary login matching auth-server/compose.yaml's own standalone
-- MySQL container, and application.yaml's hardcoded DEFAULT-profile
-- datasource credentials (both are lab-only fixed values, not env-driven,
-- so hardcoding here too is consistent rather than a shortcut). Added so
-- this container alone — via compose.yml's extra 3307 port — satisfies
-- both the devpod profile (dev/dev, above) and the default profile,
-- without ever needing to run auth-server/compose.yaml as well.
CREATE USER IF NOT EXISTS 'authuser'@'%' IDENTIFIED BY 'authpw';
GRANT ALL PRIVILEGES ON authdb.* TO 'authuser'@'%';
FLUSH PRIVILEGES;
