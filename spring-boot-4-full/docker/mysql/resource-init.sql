-- Secondary login matching resource-server/compose.yaml's own standalone
-- MySQL container, and application.yaml's hardcoded DEFAULT-profile
-- datasource credentials (both are lab-only fixed values, not env-driven,
-- so hardcoding here too is consistent rather than a shortcut). Added so
-- this container alone — via compose.yml's extra 3308 port — satisfies
-- both the devpod profile (dev/dev, in compose.yml) and the default
-- profile, without ever needing to run resource-server/compose.yaml as well.
CREATE USER IF NOT EXISTS 'resourceuser'@'%' IDENTIFIED BY 'resourcepw';
GRANT ALL PRIVILEGES ON resourcedb.* TO 'resourceuser'@'%';
FLUSH PRIVILEGES;
