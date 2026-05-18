package com.helpdesk.ticket.service;

import com.helpdesk.ticket.client.DirectoryClient;
import com.helpdesk.ticket.client.DirectoryClient.UserJson;
import com.helpdesk.ticket.client.NotificationClient;
import com.helpdesk.ticket.client.NotificationClient.NotificationType;
import com.helpdesk.ticket.dto.CommentDTO;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.repository.AttachmentRepository;
import com.helpdesk.ticket.storage.AttachmentStorageService;
import com.helpdesk.ticket.dto.TicketDTO;
import com.helpdesk.ticket.entity.Comment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.exception.TicketAccessDeniedException;
import com.helpdesk.ticket.exception.TicketNotFoundException;
import com.helpdesk.ticket.repository.CommentRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Ticket Service - Business logic for ticket management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final AttachmentRepository attachmentRepository;
    private final AttachmentStorageService attachmentStorage;
    private final DirectoryClient directoryClient;
    private final NotificationClient notificationClient;

    private String ticketLabel(Ticket t) {
        return t.getTicketNumber() != null ? t.getTicketNumber() : ("#" + t.getId());
    }

    /**
     * Create a new ticket — {@code createdBy} is taken from the JWT gateway headers when present.
     */
    public TicketDTO createTicket(TicketDTO ticketDTO, Long createdByFromGateway) {
        log.info("Creating new ticket with title: {}", ticketDTO.getTitle());

        Long createdBy = createdByFromGateway != null ? createdByFromGateway : ticketDTO.getCreatedBy();
        if (createdBy == null) {
            throw new IllegalArgumentException("createdBy is required (JWT subject or request body)");
        }

        // HESK convention: brand-new tickets start in NEW status (not OPEN —
        // OPEN means an admin/tech has at least seen it).
        Ticket ticket = Ticket.builder()
            .title(ticketDTO.getTitle())
            .description(ticketDTO.getDescription())
            .priority(Ticket.TicketPriority.valueOf(ticketDTO.getPriority()))
            .category(ticketDTO.getCategory())
            .status(Ticket.TicketStatus.NEW)
            .createdBy(createdBy)
            .assignedTo(ticketDTO.getAssignedTo())
            .linkedAssetId(ticketDTO.getLinkedAssetId())
            .building(ticketDTO.getBuilding())
            .locationDepartment(ticketDTO.getLocationDepartment())
            .roomNumber(ticketDTO.getRoomNumber())
            .locationNotes(ticketDTO.getLocationNotes())
            .build();

        Ticket savedTicket = ticketRepository.save(ticket);
        // Generate the public ticket number now that we have an auto-id.
        // Format: TKT-000001 — pads to 6 digits, easy to read in logs/UI.
        if (savedTicket.getTicketNumber() == null) {
            savedTicket.setTicketNumber(String.format("TKT-%06d", savedTicket.getId()));
            savedTicket = ticketRepository.save(savedTicket);
        }
        log.info("Ticket created successfully with ID: {} ({})",
                savedTicket.getId(), savedTicket.getTicketNumber());

        // Notify creator (confirmation) and assigned tech if pre-assigned.
        notificationClient.notify(
            savedTicket.getCreatedBy(),
            "Ticket " + ticketLabel(savedTicket) + " created",
            "Your request \"" + savedTicket.getTitle() + "\" has been received.",
            NotificationType.TICKET_CREATED, savedTicket.getId());
        if (savedTicket.getAssignedTo() != null) {
            notificationClient.notify(
                savedTicket.getAssignedTo(),
                "Ticket " + ticketLabel(savedTicket) + " assigned to you",
                "You were assigned: " + savedTicket.getTitle(),
                NotificationType.TICKET_ASSIGNED, savedTicket.getId());
        }

        return enrichDto(TicketDTO.fromEntity(savedTicket));
    }

    /**
     * Get all tickets
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getAllTickets() {
        log.info("Fetching all tickets");
        return ticketRepository.findAll().stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get ticket by ID
     */
    @Transactional(readOnly = true)
    public TicketDTO getTicketById(Long id) {
        log.info("Fetching ticket with ID: {}", id);
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> {
                log.error("Ticket not found with ID: {}", id);
                return new TicketNotFoundException("Ticket not found with ID: " + id);
            });
        return enrichDto(TicketDTO.fromEntity(ticket));
    }

    /**
     * Role-aware ticket fetch for UI detail views.
     */
    @Transactional(readOnly = true)
    public TicketDTO getTicketForViewer(Long id, String roleHeader, Long requesterId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));

        String role = roleHeader == null ? "" : roleHeader.trim().toUpperCase();

        if ("ADMIN".equals(role)) {
            return enrichDto(TicketDTO.fromEntity(ticket));
        }
        if (requesterId == null) {
            throw new TicketAccessDeniedException("Authentication required");
        }
        if ("TECHNICIAN".equals(role)) {
            if (ticket.getAssignedTo() != null && ticket.getAssignedTo().equals(requesterId)) {
                return enrichDto(TicketDTO.fromEntity(ticket));
            }
            throw new TicketAccessDeniedException("You can only view tickets assigned to you.");
        }
        if ("EMPLOYEE".equals(role)) {
            if (ticket.getCreatedBy().equals(requesterId)) {
                return enrichDto(TicketDTO.fromEntity(ticket));
            }
            throw new TicketAccessDeniedException("You can only view your own tickets.");
        }

        throw new TicketAccessDeniedException("Insufficient permissions to view this ticket.");
    }

    /**
     * Role-aware wrapper around {@link #updateTicket}. Enforces who may edit
     * what. Returns 403 (via TicketAccessDeniedException) on policy failure.
     */
    public TicketDTO updateTicketForCaller(Long id, TicketDTO patch, String roleHeader, Long requesterId) {
        Ticket existing = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
        boolean admin = isAdmin(roleHeader);
        boolean staff = isStaff(roleHeader);
        boolean owner = requesterId != null && requesterId.equals(existing.getCreatedBy());
        boolean assignedToMe = requesterId != null && requesterId.equals(existing.getAssignedTo());

        if (!admin && !owner && !(staff && assignedToMe)) {
            throw new TicketAccessDeniedException("You may not edit this ticket.");
        }

        if (owner && !staff) {
            // End-users: only before RESOLVED/CLOSED/CANCELLED, and only safe fields.
            Ticket.TicketStatus s = existing.getStatus();
            if (s == Ticket.TicketStatus.RESOLVED || s == Ticket.TicketStatus.CLOSED
                    || s == Ticket.TicketStatus.CANCELLED) {
                throw new TicketAccessDeniedException(
                    "This ticket is " + s + " and can no longer be edited by the requester.");
            }
            // Strip restricted fields so users cannot self-assign/self-promote.
            patch.setStatus(null);
            patch.setAssignedTo(null);
            patch.setLinkedAssetId(null);
        }
        if (staff && !admin && !assignedToMe) {
            throw new TicketAccessDeniedException("Technicians may only edit tickets assigned to them.");
        }

        return updateTicket(id, patch);
    }

    /**
     * Update ticket
     */
    public TicketDTO updateTicket(Long id, TicketDTO ticketDTO) {
        log.info("Updating ticket with ID: {}", id);

        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));

        if (ticketDTO.getTitle() != null) {
            ticket.setTitle(ticketDTO.getTitle());
        }
        if (ticketDTO.getDescription() != null) {
            ticket.setDescription(ticketDTO.getDescription());
        }
        if (ticketDTO.getPriority() != null) {
            ticket.setPriority(Ticket.TicketPriority.valueOf(ticketDTO.getPriority()));
        }
        if (ticketDTO.getCategory() != null) {
            ticket.setCategory(ticketDTO.getCategory());
        }
        if (ticketDTO.getStatus() != null) {
            ticket.setStatus(Ticket.TicketStatus.valueOf(ticketDTO.getStatus()));
        }
        if (ticketDTO.getAssignedTo() != null) {
            ticket.setAssignedTo(ticketDTO.getAssignedTo());
        }
        if (ticketDTO.getLinkedAssetId() != null) {
            ticket.setLinkedAssetId(ticketDTO.getLinkedAssetId());
        }
        if (ticketDTO.getBuilding() != null) {
            ticket.setBuilding(ticketDTO.getBuilding());
        }
        if (ticketDTO.getLocationDepartment() != null) {
            ticket.setLocationDepartment(ticketDTO.getLocationDepartment());
        }
        if (ticketDTO.getRoomNumber() != null) {
            ticket.setRoomNumber(ticketDTO.getRoomNumber());
        }
        if (ticketDTO.getLocationNotes() != null) {
            ticket.setLocationNotes(ticketDTO.getLocationNotes());
        }

        // Status-driven timestamps. Only stamp resolvedAt/closedAt the first
        // time the ticket enters those states, so re-saves don't keep updating
        // the stamps.
        if (ticket.getStatus() == Ticket.TicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (ticket.getStatus() == Ticket.TicketStatus.CLOSED && ticket.getClosedAt() == null) {
            ticket.setClosedAt(LocalDateTime.now());
        }

        Ticket updatedTicket = ticketRepository.save(ticket);
        log.info("Ticket updated successfully with ID: {}", id);

        return enrichDto(TicketDTO.fromEntity(updatedTicket));
    }

    /**
     * End-user cancels an open ticket; administrators may cancel most non-closed states.
     */
    public TicketDTO cancelTicket(Long id, String requesterRole, Long requesterId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
        boolean admin = isAdmin(requesterRole);
        boolean owner = requesterId != null && requesterId.equals(ticket.getCreatedBy());
        if (!admin && !owner) {
            throw new IllegalArgumentException("Only the ticket creator or an administrator may cancel this ticket.");
        }
        if (ticket.getStatus() == Ticket.TicketStatus.CANCELLED) {
            return enrichDto(TicketDTO.fromEntity(ticket));
        }
        if (ticket.getStatus() == Ticket.TicketStatus.RESOLVED
                || ticket.getStatus() == Ticket.TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Cannot cancel a resolved or closed ticket.");
        }
        ticket.setStatus(Ticket.TicketStatus.CANCELLED);
        Ticket saved = ticketRepository.save(ticket);
        // Notify assignee (if any) and the creator-if-admin-cancelled.
        if (saved.getAssignedTo() != null) {
            notificationClient.notify(
                saved.getAssignedTo(),
                "Ticket " + ticketLabel(saved) + " cancelled",
                "The ticket was cancelled.",
                NotificationType.TICKET_CLOSED, saved.getId());
        }
        if (admin && !owner) {
            notificationClient.notify(
                saved.getCreatedBy(),
                "Ticket " + ticketLabel(saved) + " cancelled by administrator",
                "Your ticket was cancelled by an administrator.",
                NotificationType.TICKET_CLOSED, saved.getId());
        }
        return enrichDto(TicketDTO.fromEntity(saved));
    }

    /**
     * Permanent delete — wipes the ticket, its comments, and any attachments
     * from disk. Two policies:
     *   • ADMIN: may delete any ticket in any state.
     *   • TECHNICIAN: may delete only tickets assigned to them, and only
     *     when the ticket is in a terminal state (RESOLVED / CLOSED /
     *     CANCELLED). This stops a tech from deleting an active ticket
     *     out from under a user mid-conversation.
     * Everyone else gets 403.
     */
    public void deleteTicket(Long id, String requesterRole, Long requesterId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));

        boolean admin = isAdmin(requesterRole);
        boolean technician = "TECHNICIAN".equalsIgnoreCase(requesterRole);

        if (admin) {
            // OK — full power.
        } else if (technician) {
            if (requesterId == null || !requesterId.equals(ticket.getAssignedTo())) {
                throw new IllegalArgumentException(
                    "Technicians can only permanently delete tickets assigned to them.");
            }
            Ticket.TicketStatus s = ticket.getStatus();
            if (s != Ticket.TicketStatus.RESOLVED
                    && s != Ticket.TicketStatus.CLOSED
                    && s != Ticket.TicketStatus.CANCELLED) {
                throw new IllegalArgumentException(
                    "Only resolved, closed, or cancelled tickets can be permanently deleted by a technician.");
            }
        } else {
            throw new IllegalArgumentException(
                "Only TECHNICIAN or ADMIN may permanently delete a ticket.");
        }

        log.info("Deleting ticket with ID: {} (requester={} role={})", id, requesterId, requesterRole);

        List<Comment> comments = commentRepository.findByTicketId(id);
        commentRepository.deleteAll(comments);

        // Best-effort attachment cleanup: drop the rows and remove the files
        // from disk. If a file is already missing, the storage delete is a
        // no-op so we don't fail the ticket delete.
        List<Attachment> atts = attachmentRepository.findByTicketIdOrderByCreatedAtAsc(id);
        for (Attachment a : atts) {
            attachmentStorage.delete(a.getStoredFilename());
        }
        attachmentRepository.deleteAll(atts);

        ticketRepository.deleteById(id);
        log.info("Ticket deleted successfully with ID: {}", id);
    }

    /**
     * Search tickets by title or description
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> searchTickets(String query) {
        log.info("Searching tickets with query: {}", query);
        return ticketRepository.searchTickets(query).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get tickets by status
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getTicketsByStatus(String status) {
        log.info("Fetching tickets with status: {}", status);
        return ticketRepository.findByStatus(Ticket.TicketStatus.valueOf(status)).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get tickets by priority
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getTicketsByPriority(String priority) {
        log.info("Fetching tickets with priority: {}", priority);
        return ticketRepository.findByPriority(Ticket.TicketPriority.valueOf(priority)).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get tickets by category
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getTicketsByCategory(String category) {
        log.info("Fetching tickets with category: {}", category);
        return ticketRepository.findByCategory(category).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get tickets created by user
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getTicketsByCreatedBy(Long userId) {
        log.info("Fetching tickets created by user: {}", userId);
        return ticketRepository.findByCreatedBy(userId).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Get tickets assigned to user
     */
    @Transactional(readOnly = true)
    public List<TicketDTO> getTicketsByAssignedTo(Long userId) {
        log.info("Fetching tickets assigned to user: {}", userId);
        return ticketRepository.findByAssignedTo(userId).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /**
     * Add comment — {@code userIdFromGateway} overrides body when present (from JWT).
     *
     * Internal-note rules:
     *   - {@code isInternal} can only be {@code true} when the requester is
     *     TECHNICIAN or ADMIN. The controller is responsible for enforcing this
     *     before reaching the service; the service trusts the flag it receives.
     */
    public CommentDTO addComment(Long ticketId, CommentDTO commentDTO, Long userIdFromGateway) {
        log.info("Adding comment to ticket ID: {} (internal={})",
                ticketId, Boolean.TRUE.equals(commentDTO.getIsInternal()));

        if (!ticketRepository.existsById(ticketId)) {
            throw new TicketNotFoundException("Ticket not found with ID: " + ticketId);
        }

        Long userId = userIdFromGateway != null ? userIdFromGateway : commentDTO.getUserId();
        if (userId == null) {
            throw new IllegalArgumentException("userId is required for comments");
        }

        boolean internal = Boolean.TRUE.equals(commentDTO.getIsInternal());

        Comment comment = Comment.builder()
            .ticketId(ticketId)
            .userId(userId)
            .commentText(commentDTO.getCommentText())
            .isInternal(internal)
            .build();

        Comment savedComment = commentRepository.save(comment);
        log.info("Comment added successfully to ticket ID: {}", ticketId);

        // Notify the "other side" of the conversation. Internal notes only
        // notify staff (assignee); public replies notify the opposite party.
        ticketRepository.findById(ticketId).ifPresent(t -> {
            String label = ticketLabel(t);
            if (internal) {
                if (t.getAssignedTo() != null && !t.getAssignedTo().equals(userId)) {
                    notificationClient.notify(
                        t.getAssignedTo(),
                        "Internal note on " + label,
                        "An internal note was added.",
                        NotificationType.COMMENT_ADDED, t.getId());
                }
            } else {
                // Public reply: if author is the creator, notify assignee; otherwise notify creator.
                Long target = userId.equals(t.getCreatedBy()) ? t.getAssignedTo() : t.getCreatedBy();
                if (target != null && !target.equals(userId)) {
                    notificationClient.notify(
                        target,
                        "New reply on " + label,
                        "A reply was posted on the ticket.",
                        NotificationType.COMMENT_ADDED, t.getId());
                }
            }
        });

        return enrichComment(CommentDTO.fromEntity(savedComment));
    }

    /**
     * Get comments for ticket — role-aware.
     *
     * @param viewerRole role from {@code X-User-Role} header. Anything other
     *                   than TECHNICIAN/ADMIN is treated as a normal end-user
     *                   and only sees public replies (isInternal=false).
     */
    @Transactional(readOnly = true)
    public List<CommentDTO> getTicketComments(Long ticketId, String viewerRole) {
        log.info("Fetching comments for ticket ID: {} as role: {}", ticketId, viewerRole);

        if (!ticketRepository.existsById(ticketId)) {
            throw new TicketNotFoundException("Ticket not found with ID: " + ticketId);
        }

        boolean canSeeInternal = isStaff(viewerRole);
        List<Comment> comments = canSeeInternal
                ? commentRepository.findByTicketId(ticketId)
                : commentRepository.findByTicketIdAndIsInternalFalse(ticketId);

        List<CommentDTO> list = comments.stream()
            .map(CommentDTO::fromEntity)
            .collect(Collectors.toList());
        enrichComments(list);
        return list;
    }

    /** Whether the given role string can see internal notes / staff data. */
    public static boolean isStaff(String role) {
        if (role == null) return false;
        String r = role.trim().toUpperCase();
        return "TECHNICIAN".equals(r) || "ADMIN".equals(r);
    }

    /** Whether the role string is ADMIN (case-insensitive). */
    public static boolean isAdmin(String role) {
        return role != null && "ADMIN".equalsIgnoreCase(role.trim());
    }

    // --------------------------------------------------------------------
    //  Role-scoped ticket queries (HESK Priority 3.1).
    //  Each method takes the requester's identity from gateway headers and
    //  scopes the result accordingly.
    // --------------------------------------------------------------------

    /** Tickets created by the calling user. */
    @Transactional(readOnly = true)
    public List<TicketDTO> getMyTickets(Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
        return ticketRepository.findByCreatedBy(requesterId).stream()
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    /** Tickets assigned to the calling technician. */
    @Transactional(readOnly = true)
    public List<TicketDTO> getMyQueue(Long requesterId) {
        if (requesterId == null) {
            throw new IllegalArgumentException("X-User-Id header is required");
        }
        return ticketRepository.findByAssignedTo(requesterId).stream()
            .filter(t -> t.getStatus() != Ticket.TicketStatus.CANCELLED)
            .map(TicketDTO::fromEntity)
            .map(this::enrichDto)
            .collect(Collectors.toList());
    }

    // --------------------------------------------------------------------
    //  Targeted PATCH operations.
    // --------------------------------------------------------------------

    /**
     * Assign (or unassign — pass null) a ticket to a technician/admin.
     * Side-effect: if the ticket is in NEW or OPEN, transition to ASSIGNED.
     */
    public TicketDTO assignTicket(Long id, Long assignedTo, String requesterRole) {
        if (!isStaff(requesterRole)) {
            throw new IllegalArgumentException("Only TECHNICIAN or ADMIN may assign tickets.");
        }
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));

        Long previous = ticket.getAssignedTo();
        ticket.setAssignedTo(assignedTo);
        if (assignedTo != null
                && (ticket.getStatus() == Ticket.TicketStatus.NEW
                    || ticket.getStatus() == Ticket.TicketStatus.OPEN)) {
            ticket.setStatus(Ticket.TicketStatus.ASSIGNED);
        }
        Ticket saved = ticketRepository.save(ticket);

        String label = ticketLabel(saved);
        if (assignedTo != null && !assignedTo.equals(previous)) {
            notificationClient.notify(
                assignedTo,
                "Ticket " + label + " assigned to you",
                "You were assigned: " + saved.getTitle(),
                NotificationType.TICKET_ASSIGNED, saved.getId());
            notificationClient.notify(
                saved.getCreatedBy(),
                "Ticket " + label + " was assigned",
                "Your ticket has been assigned to a technician.",
                NotificationType.TICKET_UPDATED, saved.getId());
        } else if (assignedTo == null && previous != null) {
            notificationClient.notify(
                previous,
                "Ticket " + label + " unassigned",
                "You are no longer assigned to: " + saved.getTitle(),
                NotificationType.TICKET_UPDATED, saved.getId());
        }
        return enrichDto(TicketDTO.fromEntity(saved));
    }

    /**
     * Change priority. Staff only. Admins may always change; technicians may
     * change priority on tickets assigned to them.
     */
    public TicketDTO changePriority(Long id, String priorityStr, String requesterRole, Long requesterId) {
        if (!isStaff(requesterRole)) {
            throw new IllegalArgumentException("Only TECHNICIAN or ADMIN may change priority.");
        }
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
        if (!isAdmin(requesterRole)
                && requesterId != null
                && !requesterId.equals(ticket.getAssignedTo())) {
            throw new IllegalArgumentException(
                "Technicians can only change priority on tickets assigned to them.");
        }
        ticket.setPriority(Ticket.TicketPriority.valueOf(priorityStr));
        return enrichDto(TicketDTO.fromEntity(ticketRepository.save(ticket)));
    }

    /**
     * Change status — same scoping rules as priority. Stamps resolvedAt /
     * closedAt the first time we hit those states (see updateTicket).
     */
    public TicketDTO changeStatus(Long id, String statusStr, String requesterRole, Long requesterId) {
        if (!isStaff(requesterRole)) {
            throw new IllegalArgumentException("Only TECHNICIAN or ADMIN may change status.");
        }
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
        if (!isAdmin(requesterRole)
                && requesterId != null
                && !requesterId.equals(ticket.getAssignedTo())) {
            throw new IllegalArgumentException(
                "Technicians can only change status on tickets assigned to them.");
        }

        Ticket.TicketStatus prev = ticket.getStatus();
        Ticket.TicketStatus next = Ticket.TicketStatus.valueOf(statusStr);
        ticket.setStatus(next);
        if (next == Ticket.TicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(LocalDateTime.now());
        }
        if (next == Ticket.TicketStatus.CLOSED && ticket.getClosedAt() == null) {
            ticket.setClosedAt(LocalDateTime.now());
        }
        Ticket saved = ticketRepository.save(ticket);

        if (prev != next) {
            NotificationType nt = (next == Ticket.TicketStatus.RESOLVED || next == Ticket.TicketStatus.CLOSED)
                ? NotificationType.TICKET_CLOSED : NotificationType.TICKET_UPDATED;
            notificationClient.notify(
                saved.getCreatedBy(),
                "Ticket " + ticketLabel(saved) + " — " + next.name().replace('_', ' '),
                "Status changed from " + prev + " to " + next + ".",
                nt, saved.getId());
        }
        return enrichDto(TicketDTO.fromEntity(saved));
    }

    /**
     * Reopen a RESOLVED or CLOSED ticket. Allowed for the original creator
     * (so end-users can self-serve) AND for staff. Returns to IN_PROGRESS.
     */
    public TicketDTO reopenTicket(Long id, String requesterRole, Long requesterId) {
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));

        boolean staff = isStaff(requesterRole);
        boolean owner = requesterId != null && requesterId.equals(ticket.getCreatedBy());
        if (!staff && !owner) {
            throw new IllegalArgumentException("Only the ticket creator or staff may reopen.");
        }
        if (ticket.getStatus() != Ticket.TicketStatus.RESOLVED
                && ticket.getStatus() != Ticket.TicketStatus.CLOSED) {
            throw new IllegalArgumentException("Only RESOLVED or CLOSED tickets can be reopened.");
        }

        ticket.setStatus(Ticket.TicketStatus.REOPENED);
        // Clear closure stamps so a future close re-stamps cleanly.
        ticket.setClosedAt(null);
        ticket.setResolvedAt(null);
        Ticket saved = ticketRepository.save(ticket);
        if (saved.getAssignedTo() != null) {
            notificationClient.notify(
                saved.getAssignedTo(),
                "Ticket " + ticketLabel(saved) + " reopened",
                "The ticket has been reopened and needs attention.",
                NotificationType.TICKET_UPDATED, saved.getId());
        }
        return enrichDto(TicketDTO.fromEntity(saved));
    }

    /** Link a ticket to an asset (or unlink — pass null). Staff only. */
    public TicketDTO linkAsset(Long id, Long assetId, String requesterRole) {
        if (!isStaff(requesterRole)) {
            throw new IllegalArgumentException("Only TECHNICIAN or ADMIN may link assets.");
        }
        Ticket ticket = ticketRepository.findById(id)
            .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + id));
        ticket.setLinkedAssetId(assetId);
        Ticket saved = ticketRepository.save(ticket);
        notificationClient.notify(
            saved.getCreatedBy(),
            "Ticket " + ticketLabel(saved) + " — asset " + (assetId == null ? "unlinked" : "linked"),
            assetId == null ? "Linked asset removed." : "An asset was linked to your ticket.",
            NotificationType.TICKET_UPDATED, saved.getId());
        return enrichDto(TicketDTO.fromEntity(saved));
    }

    /**
     * Delete comment
     */
    public void deleteComment(Long commentId) {
        log.info("Deleting comment with ID: {}", commentId);

        if (!commentRepository.existsById(commentId)) {
            throw new TicketNotFoundException("Comment not found with ID: " + commentId);
        }

        commentRepository.deleteById(commentId);
        log.info("Comment deleted successfully with ID: {}", commentId);
    }

    private TicketDTO enrichDto(TicketDTO dto) {
        if (dto.getCreatedBy() != null) {
            directoryClient.getUser(dto.getCreatedBy()).ifPresent(u -> {
                dto.setCreatedByName(formatUserName(u));
                dto.setCreatedByEmail(u.getEmail());
                dto.setCreatedByDepartment(u.getDepartment());
            });
        }
        if (dto.getAssignedTo() != null) {
            directoryClient.getUser(dto.getAssignedTo()).ifPresent(u -> {
                dto.setAssignedToName(formatUserName(u));
                dto.setAssignedToEmail(u.getEmail());
            });
        }
        if (dto.getLinkedAssetId() != null) {
            directoryClient.getAsset(dto.getLinkedAssetId()).ifPresent(a -> {
                String label = a.getName() != null ? a.getName() : "Asset";
                if (a.getSerialNumber() != null && !a.getSerialNumber().isBlank()) {
                    label = label + " (" + a.getSerialNumber() + ")";
                }
                if (a.getStatus() != null) {
                    label = label + " — " + a.getStatus();
                }
                dto.setLinkedAssetLabel(label);
            });
        }
        return dto;
    }

    private CommentDTO enrichComment(CommentDTO c) {
        enrichComments(List.of(c));
        return c;
    }

    private void enrichComments(List<CommentDTO> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Map<Long, UserJson> cache = new HashMap<>();
        for (CommentDTO c : list) {
            Long uid = c.getUserId();
            if (uid == null) {
                continue;
            }
            cache.computeIfAbsent(uid, id -> directoryClient.getUser(id).orElse(null));
        }
        for (CommentDTO c : list) {
            UserJson u = cache.get(c.getUserId());
            if (u != null) {
                c.setAuthorName(formatUserName(u));
                c.setAuthorRole(u.getRole());
                c.setAuthorAvatarUrl(u.getProfileImageUrl());
            }
        }
    }

    private static String formatUserName(UserJson u) {
        if (u == null) {
            return null;
        }
        String fn = u.getFirstName() != null ? u.getFirstName() : "";
        String ln = u.getLastName() != null ? u.getLastName() : "";
        String s = (fn + " " + ln).trim();
        return s.isEmpty() ? u.getEmail() : s;
    }
}
