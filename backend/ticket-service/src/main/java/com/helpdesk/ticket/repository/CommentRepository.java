package com.helpdesk.ticket.repository;

import com.helpdesk.ticket.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Comment Repository
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Find all comments (public + internal) — used by TECHNICIAN/ADMIN.
     */
    List<Comment> findByTicketId(Long ticketId);

    /**
     * Find only public replies for a ticket — used to render USER-facing
     * threads where internal notes must be hidden.
     */
    List<Comment> findByTicketIdAndIsInternalFalse(Long ticketId);

    /**
     * Find comments by user ID
     */
    List<Comment> findByUserId(Long userId);
}
