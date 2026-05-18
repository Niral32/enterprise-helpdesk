package com.helpdesk.notification.service;

import com.helpdesk.notification.dto.NotificationDTO;
import com.helpdesk.notification.entity.Notification;
import com.helpdesk.notification.exception.NotificationNotFoundException;
import com.helpdesk.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Notification Service - Business logic for notification management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * Create a new notification
     */
    public NotificationDTO createNotification(NotificationDTO notificationDTO) {
        log.info("Creating notification for user: {}", notificationDTO.getUserId());

        Notification notification = Notification.builder()
            .userId(notificationDTO.getUserId())
            .title(notificationDTO.getTitle())
            .message(notificationDTO.getMessage())
            .type(Notification.NotificationType.valueOf(notificationDTO.getType()))
            .isRead(false)
            .entityType(notificationDTO.getEntityType())
            .entityId(notificationDTO.getEntityId())
            .build();

        Notification savedNotification = notificationRepository.save(notification);
        log.info("Notification created successfully with ID: {}", savedNotification.getId());

        return NotificationDTO.fromEntity(savedNotification);
    }

    /**
     * Get all notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUserNotifications(Long userId) {
        log.info("Fetching notifications for user: {}", userId);
        return notificationRepository.findByUserId(userId).stream()
            .map(NotificationDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get unread notifications for a user
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(Long userId) {
        log.info("Fetching unread notifications for user: {}", userId);
        return notificationRepository.findByUserIdAndIsReadFalse(userId).stream()
            .map(NotificationDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get notification by ID
     */
    @Transactional(readOnly = true)
    public NotificationDTO getNotificationById(Long id) {
        log.info("Fetching notification with ID: {}", id);
        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Notification not found with ID: {}", id);
                return new NotificationNotFoundException("Notification not found with ID: " + id);
            });
        return NotificationDTO.fromEntity(notification);
    }

    /**
     * Mark notification as read
     */
    public NotificationDTO markAsRead(Long id, Long requesterUserId) {
        log.info("Marking notification as read: {}", id);

        Notification notification = notificationRepository.findById(id)
            .orElseThrow(() -> new NotificationNotFoundException("Notification not found with ID: " + id));
        if (requesterUserId != null && !requesterUserId.equals(notification.getUserId())) {
            throw new IllegalArgumentException("You may not modify another user's notification.");
        }

        notification.setIsRead(true);
        Notification updatedNotification = notificationRepository.save(notification);

        return NotificationDTO.fromEntity(updatedNotification);
    }

    /**
     * Mark all notifications as read for a user
     */
    public void markAllAsRead(Long userId) {
        log.info("Marking all notifications as read for user: {}", userId);

        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unreadNotifications.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    /**
     * Delete notification
     */
    public void deleteNotification(Long id) {
        log.info("Deleting notification with ID: {}", id);

        if (!notificationRepository.existsById(id)) {
            throw new NotificationNotFoundException("Notification not found with ID: " + id);
        }

        notificationRepository.deleteById(id);
        log.info("Notification deleted successfully with ID: {}", id);
    }

    /**
     * Delete all notifications for a user
     */
    public void deleteAllUserNotifications(Long userId) {
        log.info("Deleting all notifications for user: {}", userId);

        List<Notification> notifications = notificationRepository.findByUserId(userId);
        notificationRepository.deleteAll(notifications);
    }

    /**
     * Get unread count for user
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        log.info("Getting unread notification count for user: {}", userId);
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Get notifications by type
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotificationsByType(String type) {
        log.info("Fetching notifications by type: {}", type);
        return notificationRepository.findByType(Notification.NotificationType.valueOf(type)).stream()
            .map(NotificationDTO::fromEntity)
            .collect(Collectors.toList());
    }

    /**
     * Get notifications by user and type
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUserNotificationsByType(Long userId, String type) {
        log.info("Fetching notifications for user: {} with type: {}", userId, type);
        return notificationRepository.findByUserIdAndType(userId, Notification.NotificationType.valueOf(type)).stream()
            .map(NotificationDTO::fromEntity)
            .collect(Collectors.toList());
    }
}
