-- ============================================================================
-- V1 — user_db baseline schema
-- See backend/auth-service/src/main/resources/db/migration/V1__init.sql for
-- the rationale on why this V1 exists alongside baseline-on-migrate.
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
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
