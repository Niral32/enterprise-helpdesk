package com.helpdesk.ticket.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Comment Entity - Comments on tickets
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String commentText;

    /**
     * Internal notes are only visible to TECHNICIAN and ADMIN roles.
     * Public replies (isInternal = false / null) are visible to the ticket
     * creator too.
     *
     * Column is nullable so {@code spring.jpa.hibernate.ddl-auto: update}
     * can add it cleanly to existing tables without a default-value migration.
     * All read-paths treat null as false (see {@code CommentDTO.fromEntity}).
     */
    @Builder.Default
    @Column(name = "is_internal")
    private Boolean isInternal = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
