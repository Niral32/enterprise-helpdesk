package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Attachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentDTO {
    private Long id;
    private Long ticketId;
    private Long uploadedBy;
    private String originalFilename;
    private String contentType;
    private Long sizeBytes;
    /** Relative download URL — frontend prefixes with the gateway base. */
    private String downloadUrl;
    /** Same URL but works for plain <img src=> when the asset is an image. */
    private String previewUrl;
    private LocalDateTime createdAt;

    public static AttachmentDTO fromEntity(Attachment a) {
        String url = "/api/tickets/attachments/" + a.getId() + "/download";
        return AttachmentDTO.builder()
            .id(a.getId())
            .ticketId(a.getTicketId())
            .uploadedBy(a.getUploadedBy())
            .originalFilename(a.getOriginalFilename())
            .contentType(a.getContentType())
            .sizeBytes(a.getSizeBytes())
            .downloadUrl(url)
            .previewUrl(url)
            .createdAt(a.getCreatedAt())
            .build();
    }
}
