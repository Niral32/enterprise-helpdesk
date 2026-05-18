package com.helpdesk.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * File attached to a ticket. The bytes live on disk (see AttachmentStorageService);
 * the entity only stores the metadata + the path. Cascading delete is handled
 * application-side because the ticket → attachment link crosses microservice
 * boundaries in spirit, not at the DB level.
 */
@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** Who uploaded — userId from JWT, used for permission checks on delete. */
    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, length = 255, unique = true)
    private String storedFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
