-- ============================================================================
-- V1 — ticket_db baseline schema (tickets + comments + attachments)
-- ============================================================================

CREATE TABLE IF NOT EXISTS tickets (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_number        VARCHAR(32)  DEFAULT NULL,
    title                VARCHAR(255) NOT NULL,
    description          TEXT         NOT NULL,
    priority             VARCHAR(32)  NOT NULL,
    category             VARCHAR(255) NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    created_by           BIGINT       NOT NULL,
    assigned_to          BIGINT       DEFAULT NULL,
    linked_asset_id      BIGINT       DEFAULT NULL,
    -- Optional device-location fields supplied on the ticket form
    building             VARCHAR(100) DEFAULT NULL,
    location_department  VARCHAR(100) DEFAULT NULL,
    room_number          VARCHAR(50)  DEFAULT NULL,
    location_notes       VARCHAR(500) DEFAULT NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    resolved_at          DATETIME(6)  DEFAULT NULL,
    closed_at            DATETIME(6)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_ticket_number (ticket_number),
    KEY idx_tickets_created_by (created_by),
    KEY idx_tickets_assigned_to (assigned_to),
    KEY idx_tickets_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS comments (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id     BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    comment_text  TEXT        NOT NULL,
    is_internal   BIT(1)      DEFAULT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comments_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS ticket_attachments (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    ticket_id          BIGINT       NOT NULL,
    uploaded_by        BIGINT       NOT NULL,
    original_filename  VARCHAR(255) NOT NULL,
    stored_filename    VARCHAR(255) NOT NULL,
    content_type       VARCHAR(100) DEFAULT NULL,
    size_bytes         BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_attachments_stored_filename (stored_filename),
    KEY idx_ticket_attachments_ticket_id (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
