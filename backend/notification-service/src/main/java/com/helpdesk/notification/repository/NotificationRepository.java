package com.helpdesk.notification.repository;

import com.helpdesk.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Notification Repository
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Find notifications by user ID
     */
    List<Notification> findByUserId(Long userId);

    /**
     * Find unread notifications by user ID
     */
    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    /**
     * Find notifications by type
     */
    List<Notification> findByType(Notification.NotificationType type);

    /**
     * Find notifications by user ID and type
     */
    List<Notification> findByUserIdAndType(Long userId, Notification.NotificationType type);

    /**
     * Count unread notifications for user
     */
    long countByUserIdAndIsReadFalse(Long userId);
}
