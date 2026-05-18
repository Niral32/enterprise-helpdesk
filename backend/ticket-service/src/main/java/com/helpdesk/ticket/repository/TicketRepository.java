package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ticket Repository
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /**
     * Find tickets by status
     */
    List<Ticket> findByStatus(Ticket.TicketStatus status);

    /**
     * Find tickets by priority
     */
    List<Ticket> findByPriority(Ticket.TicketPriority priority);

    /**
     * Find tickets created by user
     */
    List<Ticket> findByCreatedBy(Long userId);

    /**
     * Find tickets assigned to user
     */
    List<Ticket> findByAssignedTo(Long userId);

    /**
     * Find tickets by category
     */
    List<Ticket> findByCategory(String category);

    /**
     * Search tickets by title or description
     */
    @Query("SELECT t FROM Ticket t WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Ticket> searchTickets(@Param("query") String query);
}
