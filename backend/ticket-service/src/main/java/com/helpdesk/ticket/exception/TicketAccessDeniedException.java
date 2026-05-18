package com.helpdesk.ticket.exception;

/**
 * Thrown when a viewer is not allowed to read or mutate a ticket.
 */
public class TicketAccessDeniedException extends RuntimeException {

    public TicketAccessDeniedException(String message) {
        super(message);
    }
}
