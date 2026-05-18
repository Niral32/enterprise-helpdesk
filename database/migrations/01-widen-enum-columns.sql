-- ===========================================================================
-- Migration 01 — Widen enum-mapped columns from MySQL native ENUM to VARCHAR(32)
-- ===========================================================================
--
-- Why:
--   Hibernate 6 (Spring Boot 3.2) by default creates a MySQL native ENUM
--   column for @Enumerated(EnumType.STRING) fields. When a new value is
--   added to the Java enum later (e.g. CANCELLED on TicketStatus),
--   `ddl-auto: update` does NOT alter MySQL ENUM column definitions, so
--   inserting the new value triggers "Data truncated for column 'status'".
--
--   This script converts every enum column in every service database to a
--   plain VARCHAR(32). The application code now also forces VARCHAR(32) via
--   columnDefinition, so future enum additions will not regress.
--
-- Safety:
--   Idempotent — re-running is a no-op when columns are already VARCHAR(32).
--   Existing data is preserved (MySQL implicitly casts ENUM values to their
--   string label when the column type changes to VARCHAR).
--
-- How to apply (against the running stack):
--   docker compose exec -T mysql mysql -uroot -prootpass \
--       < database/migrations/01-widen-enum-columns.sql
--
--   Replace `rootpass` with the actual MYSQL_ROOT_PASSWORD from
--   docker-compose.yml if you customised it.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- ticket_db
-- ---------------------------------------------------------------------------
USE ticket_db;
ALTER TABLE tickets MODIFY COLUMN status   VARCHAR(32) NOT NULL;
ALTER TABLE tickets MODIFY COLUMN priority VARCHAR(32) NOT NULL;

-- ---------------------------------------------------------------------------
-- user_db
-- ---------------------------------------------------------------------------
USE user_db;
ALTER TABLE users MODIFY COLUMN role   VARCHAR(32) NOT NULL;
ALTER TABLE users MODIFY COLUMN status VARCHAR(32) NOT NULL;

-- ---------------------------------------------------------------------------
-- auth_db
-- ---------------------------------------------------------------------------
USE auth_db;
ALTER TABLE users MODIFY COLUMN role   VARCHAR(32) NOT NULL;
ALTER TABLE users MODIFY COLUMN status VARCHAR(32) NOT NULL;

-- ---------------------------------------------------------------------------
-- asset_db
-- ---------------------------------------------------------------------------
USE asset_db;
ALTER TABLE assets MODIFY COLUMN status VARCHAR(32) NOT NULL;

-- ---------------------------------------------------------------------------
-- notification_db
-- ---------------------------------------------------------------------------
USE notification_db;
ALTER TABLE notifications MODIFY COLUMN type VARCHAR(32) NOT NULL;

-- ===========================================================================
-- Migration 02 (folded in here for one-step execution)
--
-- Add profile_image_url columns and any new optional ticket location columns.
-- Hibernate's ddl-auto: update will create these for new deployments, but
-- existing prod databases on `update` mode never get column additions for
-- nullable fields that already lack them, depending on Hibernate dialect.
-- These ADDs are guarded so re-running is safe.
-- ===========================================================================

USE auth_db;
-- MySQL 8 doesn't support IF NOT EXISTS on ADD COLUMN; emulate via INFORMATION_SCHEMA.
SET @stmt = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'auth_db' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'profile_image_url') = 0,
    'ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) NULL',
    'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

USE user_db;
SET @stmt = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = 'user_db' AND TABLE_NAME = 'users' AND COLUMN_NAME = 'profile_image_url') = 0,
    'ALTER TABLE users ADD COLUMN profile_image_url VARCHAR(500) NULL',
    'SELECT 1');
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
