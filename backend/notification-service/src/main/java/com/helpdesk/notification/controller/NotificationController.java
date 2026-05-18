package com.helpdesk.notification.controller;

import com.helpdesk.notification.dto.NotificationDTO;
import com.helpdesk.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Notification Controller - REST API endpoints for notification management
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Notification management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Current user's notifications — uses {@code X-User-Id} from the gateway (no user id in URL).
     */
    @GetMapping("/me")
    @Operation(summary = "List my notifications")
    public ResponseEntity<List<NotificationDTO>> listMine(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseLongHeader(userIdHeader);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(notificationService.getUserNotifications(uid));
    }

    @GetMapping("/me/unread")
    @Operation(summary = "List my unread notifications")
    public ResponseEntity<List<NotificationDTO>> listMineUnread(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseLongHeader(userIdHeader);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(notificationService.getUnreadNotifications(uid));
    }

    @GetMapping("/me/unread-count")
    @Operation(summary = "Unread count for current user")
    public ResponseEntity<Map<String, Long>> myUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseLongHeader(userIdHeader);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        long unreadCount = notificationService.getUnreadCount(uid);
        Map<String, Long> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/me/read-all")
    @Operation(summary = "Mark all my notifications read")
    public ResponseEntity<Void> markAllMineRead(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseLongHeader(userIdHeader);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notificationService.markAllAsRead(uid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Create a new notification
     */
    @PostMapping
    @Operation(summary = "Create a new notification", description = "Create a new notification")
    public ResponseEntity<NotificationDTO> createNotification(@RequestBody NotificationDTO notificationDTO) {
        log.info("REST request to create notification");
        NotificationDTO createdNotification = notificationService.createNotification(notificationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdNotification);
    }

    /**
     * Get user notifications
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user notifications", description = "Retrieve all notifications for a user")
    public ResponseEntity<List<NotificationDTO>> getUserNotifications(@PathVariable Long userId) {
        log.info("REST request to get notifications for user: {}", userId);
        List<NotificationDTO> notifications = notificationService.getUserNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread notifications
     */
    @GetMapping("/user/{userId}/unread")
    @Operation(summary = "Get unread notifications", description = "Retrieve unread notifications for a user")
    public ResponseEntity<List<NotificationDTO>> getUnreadNotifications(@PathVariable Long userId) {
        log.info("REST request to get unread notifications for user: {}", userId);
        List<NotificationDTO> notifications = notificationService.getUnreadNotifications(userId);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get unread count
     */
    @GetMapping("/user/{userId}/unread-count")
    @Operation(summary = "Get unread count", description = "Get count of unread notifications for a user")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@PathVariable Long userId) {
        log.info("REST request to get unread count for user: {}", userId);
        long unreadCount = notificationService.getUnreadCount(userId);
        Map<String, Long> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Get notification by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID", description = "Retrieve a specific notification by its ID")
    public ResponseEntity<NotificationDTO> getNotificationById(@PathVariable Long id) {
        log.info("REST request to get notification with ID: {}", id);
        NotificationDTO notification = notificationService.getNotificationById(id);
        return ResponseEntity.ok(notification);
    }

    /**
     * Mark as read
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "Mark as read", description = "Mark a notification as read (must belong to X-User-Id)")
    public ResponseEntity<NotificationDTO> markAsRead(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to mark notification as read: {}", id);
        Long uid = parseLongHeader(userIdHeader);
        if (uid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        NotificationDTO notification = notificationService.markAsRead(id, uid);
        return ResponseEntity.ok(notification);
    }

    /**
     * Mark all as read
     */
    @PutMapping("/user/{userId}/read-all")
    @Operation(summary = "Mark all as read", description = "Mark all notifications as read for a user")
    public ResponseEntity<Void> markAllAsRead(@PathVariable Long userId) {
        log.info("REST request to mark all notifications as read for user: {}", userId);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete notification
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete notification", description = "Delete a notification by its ID")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        log.info("REST request to delete notification: {}", id);
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete all user notifications
     */
    @DeleteMapping("/user/{userId}/all")
    @Operation(summary = "Delete all notifications", description = "Delete all notifications for a user")
    public ResponseEntity<Void> deleteAllUserNotifications(@PathVariable Long userId) {
        log.info("REST request to delete all notifications for user: {}", userId);
        notificationService.deleteAllUserNotifications(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get notifications by type
     */
    @GetMapping("/by-type/{type}")
    @Operation(summary = "Get notifications by type", description = "Retrieve notifications filtered by type")
    public ResponseEntity<List<NotificationDTO>> getNotificationsByType(@PathVariable String type) {
        log.info("REST request to get notifications by type: {}", type);
        List<NotificationDTO> notifications = notificationService.getNotificationsByType(type);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Get user notifications by type
     */
    @GetMapping("/user/{userId}/by-type/{type}")
    @Operation(summary = "Get user notifications by type", description = "Retrieve notifications for a user filtered by type")
    public ResponseEntity<List<NotificationDTO>> getUserNotificationsByType(@PathVariable Long userId, @PathVariable String type) {
        log.info("REST request to get notifications for user: {} with type: {}", userId, type);
        List<NotificationDTO> notifications = notificationService.getUserNotificationsByType(userId, type);
        return ResponseEntity.ok(notifications);
    }

    private static Long parseLongHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
