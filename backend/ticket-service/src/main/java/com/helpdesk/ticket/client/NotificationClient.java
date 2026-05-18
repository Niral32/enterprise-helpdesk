package com.helpdesk.ticket.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Fire-and-forget notification creator. Failures are logged but never bubbled
 * up — a notification miss must not block the underlying ticket operation.
 */
@Component
@Slf4j
public class NotificationClient {

    private final RestTemplate restTemplate;

    @Value("${helpdesk.notification-service.base-url:http://localhost:8005}")
    private String notificationServiceBaseUrl;

    public NotificationClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void notify(Long userId, String title, String message, NotificationType type, Long ticketId) {
        if (userId == null) return;
        Payload body = Payload.builder()
            .userId(userId)
            .title(title)
            .message(message)
            .type(type.name())
            .entityType("TICKET")
            .entityId(ticketId)
            .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Payload> entity = new HttpEntity<>(body, headers);

        try {
            String url = notificationServiceBaseUrl.replaceAll("/$", "") + "/api/notifications";
            restTemplate.postForEntity(url, entity, Void.class);
            log.debug("Notification sent userId={} type={} ticketId={}", userId, type, ticketId);
        } catch (RestClientException ex) {
            log.warn("Could not deliver notification (userId={} type={}): {}", userId, type, ex.getMessage());
        }
    }

    public enum NotificationType {
        TICKET_CREATED,
        TICKET_ASSIGNED,
        TICKET_UPDATED,
        TICKET_CLOSED,
        COMMENT_ADDED,
        INFO
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Payload {
        private Long userId;
        private String title;
        private String message;
        private String type;
        private String entityType;
        private Long entityId;
    }
}
