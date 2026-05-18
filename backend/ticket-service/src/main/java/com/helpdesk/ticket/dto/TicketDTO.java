package com.helpdesk.ticket.dto;

import com.helpdesk.ticket.entity.Ticket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ticket DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketDTO {
    private Long id;
    /** Human-readable identifier like "TKT-000142". Read-only — set by the server. */
    private String ticketNumber;
    private String title;
    private String description;
    private String priority;
    private String category;
    private String status;
    private Long createdBy;
    private Long assignedTo;
    /** Optional ID of an Asset (in asset-service) related to this ticket. */
    private Long linkedAssetId;
    /** Populated by ticket-service when calling user/asset directory (optional). */
    private String createdByName;
    private String createdByEmail;
    private String createdByDepartment;
    private String assignedToName;
    private String assignedToEmail;
    private String linkedAssetLabel;

    // ----- Optional location of the failing device --------------------------
    private String building;
    /** Distinct from createdByDepartment — describes where the device lives, not who owns the ticket. */
    private String locationDepartment;
    private String roomNumber;
    private String locationNotes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    public static TicketDTO fromEntity(Ticket ticket) {
        return TicketDTO.builder()
            .id(ticket.getId())
            .ticketNumber(ticket.getTicketNumber())
            .title(ticket.getTitle())
            .description(ticket.getDescription())
            .priority(ticket.getPriority().toString())
            .category(ticket.getCategory())
            .status(ticket.getStatus().toString())
            .createdBy(ticket.getCreatedBy())
            .assignedTo(ticket.getAssignedTo())
            .linkedAssetId(ticket.getLinkedAssetId())
            .building(ticket.getBuilding())
            .locationDepartment(ticket.getLocationDepartment())
            .roomNumber(ticket.getRoomNumber())
            .locationNotes(ticket.getLocationNotes())
            .createdAt(ticket.getCreatedAt())
            .updatedAt(ticket.getUpdatedAt())
            .resolvedAt(ticket.getResolvedAt())
            .closedAt(ticket.getClosedAt())
            .build();
    }
}
