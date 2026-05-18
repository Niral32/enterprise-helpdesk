package com.helpdesk.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for {@code PATCH /api/tickets/{id}/priority}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriorityUpdateRequest {
    /** One of TicketPriority values: LOW, MEDIUM, HIGH, CRITICAL. */
    private String priority;
}
