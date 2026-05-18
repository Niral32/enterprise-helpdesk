package com.helpdesk.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Notification Entity - User notifications
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(32)")
    private NotificationType type;

    @Column(nullable = false)
    private Boolean isRead = false;

    @Column
    private String entityType;

    @Column
    private Long entityId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum NotificationType {
        TICKET_CREATED,
        TICKET_ASSIGNED,
        TICKET_UPDATED,
        TICKET_CLOSED,
        COMMENT_ADDED,
        ASSET_ASSIGNED,
        ASSET_RETURNED,
        SYSTEM_ALERT,
        INFO
    }
}
