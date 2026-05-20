-- ============================================================================
-- V1 — auth_db baseline schema
--
-- This file describes the schema as of the first Flyway-managed release.
-- It exists for two reasons:
--   1. Fresh deployments execute this file to create the tables.
--   2. Existing deployments are baselined past V1 (see application.yml,
--      flyway.baseline-on-migrate=true, baseline-version=1), so the file
--      is not re-executed against an already-populated database.
--
-- Every column reflects the current entity (com.helpdesk.auth.entity.User)
-- including columns added incrementally during development (profile_image_url,
-- VARCHAR-widened enum columns).
-- ============================================================================

CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    email               VARCHAR(255) NOT NULL,
    first_name          VARCHAR(255) NOT NULL,
    last_name           VARCHAR(255) NOT NULL,
    password            VARCHAR(255) NOT NULL,
    role                VARCHAR(32)  NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    department          VARCHAR(255) DEFAULT NULL,
    phone               VARCHAR(255) DEFAULT NULL,
    profile_image_url   VARCHAR(500) DEFAULT NULL,
    active              BIT(1)       NOT NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
