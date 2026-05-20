-- ============================================================================
-- V1 — notification_db baseline schema
-- ============================================================================

CREATE TABLE IF NOT EXISTS notifications (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(255) NOT NULL,
    message       TEXT         NOT NULL,
    type          VARCHAR(32)  NOT NULL,
    is_read       BIT(1)       NOT NULL,
    entity_type   VARCHAR(255) DEFAULT NULL,
    entity_id     BIGINT       DEFAULT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notifications_user_id (user_id),
    KEY idx_notifications_user_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
