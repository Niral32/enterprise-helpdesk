package com.helpdesk.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body for {@code PATCH /api/tickets/{id}/assign}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequest {
    /** ID of the technician (or admin) to assign the ticket to. Null to unassign. */
    private Long assignedTo;
}
