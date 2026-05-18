package com.helpdesk.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for {@code PATCH /api/tickets/{id}/status}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusUpdateRequest {
    /** One of TicketStatus values: NEW, OPEN, ASSIGNED, IN_PROGRESS, … */
    private String status;
}
