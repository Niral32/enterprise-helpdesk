package com.helpdesk.notification.dto;

import com.helpdesk.notification.entity.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Notification DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    private Long id;
    private Long userId;
    private String title;
    private String message;
    private String type;
    private Boolean isRead;
    private String entityType;
    private Long entityId;
    private LocalDateTime createdAt;

    public static NotificationDTO fromEntity(Notification notification) {
        return NotificationDTO.builder()
            .id(notification.getId())
            .userId(notification.getUserId())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .type(notification.getType().toString())
            .isRead(notification.getIsRead())
            .entityType(notification.getEntityType())
            .entityId(notification.getEntityId())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
