-- ============================================================================
-- V1 — asset_db baseline schema
-- ============================================================================

CREATE TABLE IF NOT EXISTS assets (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(255) NOT NULL,
    asset_type      VARCHAR(255) NOT NULL,
    serial_number   VARCHAR(255) NOT NULL,
    description     TEXT         DEFAULT NULL,
    status          VARCHAR(32)  NOT NULL,
    -- 0 means "unassigned pool" — asset_service treats 0 as the sentinel.
    assigned_to     BIGINT       NOT NULL,
    location        VARCHAR(255) DEFAULT NULL,
    purchase_date   DATETIME(6)  DEFAULT NULL,
    vendor          VARCHAR(255) DEFAULT NULL,
    cost            DOUBLE       DEFAULT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assets_serial_number (serial_number),
    KEY idx_assets_assigned_to (assigned_to),
    KEY idx_assets_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
