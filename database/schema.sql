-- ====================================================
-- Enterprise Help Desk — Bootstrap Schema
-- ====================================================
--
-- This script ONLY creates the per-service databases and the application
-- user. The actual table definitions are owned by each microservice's JPA
-- entities and generated automatically because every service runs with
-- `spring.jpa.hibernate.ddl-auto: update`.
--
-- Why not put CREATE TABLE statements here?
--   • Keeps the entity (Java) as the single source of truth for column
--     definitions, indexes, and constraints.
--   • Avoids the entity ↔ schema drift that breaks `ddl-auto: update` when
--     the entity adds a NOT NULL column or renames a field.
--   • Lets each microservice evolve its schema independently — the whole
--     point of microservices.
--
-- Sample users: employees self-register via the UI after the stack is up.
-- Technicians are created by the administrator, not via public registration.
-- A single default administrator is created by auth-service on startup when
-- no ADMIN exists (see helpdesk.bootstrap-admin in auth application.yml).
-- ====================================================

CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS user_db;
CREATE DATABASE IF NOT EXISTS ticket_db;
CREATE DATABASE IF NOT EXISTS asset_db;
CREATE DATABASE IF NOT EXISTS notification_db;

-- Application user. The MySQL container also creates this user via the
-- MYSQL_USER / MYSQL_PASSWORD env vars, but that user only gets access to
-- the single MYSQL_DATABASE the entrypoint creates. We need it to reach all
-- five service databases, so re-grant explicitly here.
CREATE USER IF NOT EXISTS 'helpdesk_user'@'%' IDENTIFIED BY 'helpdesk_pass';

GRANT ALL PRIVILEGES ON auth_db.*         TO 'helpdesk_user'@'%';
GRANT ALL PRIVILEGES ON user_db.*         TO 'helpdesk_user'@'%';
GRANT ALL PRIVILEGES ON ticket_db.*       TO 'helpdesk_user'@'%';
GRANT ALL PRIVILEGES ON asset_db.*        TO 'helpdesk_user'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'helpdesk_user'@'%';

FLUSH PRIVILEGES;
