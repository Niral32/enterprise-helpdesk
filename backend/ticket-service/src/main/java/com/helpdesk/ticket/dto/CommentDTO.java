package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Comment DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {
    private Long id;
    private Long ticketId;
    private Long userId;
    private String commentText;
    /** True for technician/admin internal notes; false for public replies. */
    private Boolean isInternal;
    /** Best-effort display fields from user directory when enriched. */
    private String authorName;
    private String authorRole;
    private String authorAvatarUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CommentDTO fromEntity(Comment comment) {
        return CommentDTO.builder()
            .id(comment.getId())
            .ticketId(comment.getTicketId())
            .userId(comment.getUserId())
            .commentText(comment.getCommentText())
            .isInternal(Boolean.TRUE.equals(comment.getIsInternal()))
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }
}
