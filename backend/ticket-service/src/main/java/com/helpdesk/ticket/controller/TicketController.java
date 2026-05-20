package com.helpdesk.ticket.controller;

import com.helpdesk.ticket.dto.AssignmentRequest;
import com.helpdesk.ticket.dto.AttachmentDTO;
import com.helpdesk.ticket.dto.CommentDTO;
import com.helpdesk.ticket.dto.LinkAssetRequest;
import com.helpdesk.ticket.dto.PriorityUpdateRequest;
import com.helpdesk.ticket.dto.StatusUpdateRequest;
import com.helpdesk.ticket.dto.TicketDTO;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.repository.AttachmentRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.storage.AttachmentStorageService;
import com.helpdesk.ticket.service.TicketService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ticket Controller — static routes ({@code /search}, …) are registered before {@code /{id}}
 * so literal segments are not captured as numeric ids.
 */
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tickets", description = "Ticket management endpoints")
@SecurityRequirement(name = "bearer-jwt")
public class TicketController {

    private final TicketService ticketService;
    private final AttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final AttachmentStorageService attachmentStorage;

    @PostMapping
    @Operation(summary = "Create a new ticket", description = "createdBy is inferred from X-User-Id when sent via gateway")
    public ResponseEntity<TicketDTO> createTicket(
            @RequestBody TicketDTO ticketDTO,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to create ticket");
        Long uid = parseUserId(userIdHeader);
        TicketDTO createdTicket = ticketService.createTicket(ticketDTO, uid);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTicket);
    }

    @GetMapping
    @Operation(summary = "Get all tickets", description = "Retrieve all tickets")
    public ResponseEntity<List<TicketDTO>> getAllTickets() {
        log.info("REST request to get all tickets");
        List<TicketDTO> tickets = ticketService.getAllTickets();
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/search")
    @Operation(summary = "Search tickets", description = "Search tickets by title or description")
    public ResponseEntity<List<TicketDTO>> searchTickets(@RequestParam String query) {
        log.info("REST request to search tickets with query: {}", query);
        List<TicketDTO> tickets = ticketService.searchTickets(query);
        return ResponseEntity.ok(tickets);
    }

    // ----------------------------------------------------------------
    //  Role-scoped views (HESK Priority 3.1)
    // ----------------------------------------------------------------

    @GetMapping("/my")
    @Operation(summary = "USER: tickets I created",
            description = "Uses X-User-Id from gateway. Internal notes never returned via /comments for non-staff.")
    public ResponseEntity<List<TicketDTO>> getMyTickets(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseUserId(userIdHeader);
        return ResponseEntity.ok(ticketService.getMyTickets(uid));
    }

    @GetMapping("/technician/queue")
    @Operation(summary = "TECHNICIAN: tickets assigned to me",
            description = "Uses X-User-Id; returns tickets where assignedTo == requester id.")
    public ResponseEntity<List<TicketDTO>> getMyQueue(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long uid = parseUserId(userIdHeader);
        return ResponseEntity.ok(ticketService.getMyQueue(uid));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN: all tickets across the system")
    public ResponseEntity<List<TicketDTO>> getAllForAdmin() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/by-status/{status}")
    @Operation(summary = "Get tickets by status")
    public ResponseEntity<List<TicketDTO>> getTicketsByStatus(@PathVariable String status) {
        log.info("REST request to get tickets by status: {}", status);
        List<TicketDTO> tickets = ticketService.getTicketsByStatus(status);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/by-priority/{priority}")
    @Operation(summary = "Get tickets by priority")
    public ResponseEntity<List<TicketDTO>> getTicketsByPriority(@PathVariable String priority) {
        log.info("REST request to get tickets by priority: {}", priority);
        List<TicketDTO> tickets = ticketService.getTicketsByPriority(priority);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/by-category/{category}")
    @Operation(summary = "Get tickets by category")
    public ResponseEntity<List<TicketDTO>> getTicketsByCategory(@PathVariable String category) {
        log.info("REST request to get tickets by category: {}", category);
        List<TicketDTO> tickets = ticketService.getTicketsByCategory(category);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/created-by/{userId}")
    @Operation(summary = "Get tickets created by user")
    public ResponseEntity<List<TicketDTO>> getTicketsByCreatedBy(@PathVariable Long userId) {
        log.info("REST request to get tickets created by user: {}", userId);
        List<TicketDTO> tickets = ticketService.getTicketsByCreatedBy(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/assigned-to/{userId}")
    @Operation(summary = "Get tickets assigned to user")
    public ResponseEntity<List<TicketDTO>> getTicketsByAssignedTo(@PathVariable Long userId) {
        log.info("REST request to get tickets assigned to user: {}", userId);
        List<TicketDTO> tickets = ticketService.getTicketsByAssignedTo(userId);
        return ResponseEntity.ok(tickets);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ticket by ID (role-scoped)")
    public ResponseEntity<TicketDTO> getTicketById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to get ticket with ID: {}", id);
        TicketDTO ticket = ticketService.getTicketForViewer(id, roleHeader, parseUserId(userIdHeader));
        return ResponseEntity.ok(ticket);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ticket",
            description = "ADMIN can change anything. TECHNICIAN can edit tickets assigned to them. "
                    + "EMPLOYEE can edit only their own tickets, and only before RESOLVED/CLOSED/CANCELLED — "
                    + "and only the editable narrative fields (title/description/category/priority).")
    public ResponseEntity<TicketDTO> updateTicket(
            @PathVariable Long id,
            @RequestBody TicketDTO ticketDTO,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to update ticket with ID: {}", id);
        TicketDTO updatedTicket = ticketService.updateTicketForCaller(
            id, ticketDTO, roleHeader, parseUserId(userIdHeader));
        return ResponseEntity.ok(updatedTicket);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete ticket permanently",
            description = "ADMIN: any ticket. TECHNICIAN: only tickets assigned to them in RESOLVED/CLOSED/CANCELLED state.")
    public ResponseEntity<Void> deleteTicket(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to delete ticket with ID: {}", id);
        ticketService.deleteTicket(id, roleHeader, parseUserId(userIdHeader));
        return ResponseEntity.noContent().build();
    }

    /**
     * Creator or admin cancels a ticket (sets status CANCELLED).
     */
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel ticket (owner or admin)")
    public ResponseEntity<TicketDTO> cancelTicket(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        TicketDTO dto = ticketService.cancelTicket(id, roleHeader, parseUserId(userIdHeader));
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{ticketId}/comments")
    @Operation(summary = "Add public reply to ticket",
            description = "Adds a public reply visible to the ticket creator. "
                    + "isInternal in the body is ignored here — use /internal-notes for staff-only notes.")
    public ResponseEntity<CommentDTO> addComment(
            @PathVariable Long ticketId,
            @RequestBody CommentDTO commentDTO,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        log.info("REST request to add public reply to ticket: {}", ticketId);
        // Force public — never let a /comments call sneak through as internal.
        commentDTO.setIsInternal(false);
        Long uid = parseUserId(userIdHeader);
        CommentDTO createdComment = ticketService.addComment(ticketId, commentDTO, uid);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdComment);
    }

    @PostMapping("/{ticketId}/internal-notes")
    @Operation(summary = "Add internal note (staff only)",
            description = "Internal notes are visible only to TECHNICIAN and ADMIN roles. "
                    + "Returns 403 if the requester is not staff.")
    public ResponseEntity<CommentDTO> addInternalNote(
            @PathVariable Long ticketId,
            @RequestBody CommentDTO commentDTO,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        log.info("REST request to add internal note to ticket: {}", ticketId);
        if (!TicketService.isStaff(roleHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        commentDTO.setIsInternal(true);
        Long uid = parseUserId(userIdHeader);
        CommentDTO createdComment = ticketService.addComment(ticketId, commentDTO, uid);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdComment);
    }

    @GetMapping("/{ticketId}/comments")
    @Operation(summary = "Get ticket comments",
            description = "USER role sees only public replies; TECHNICIAN/ADMIN see public + internal.")
    public ResponseEntity<List<CommentDTO>> getTicketComments(
            @PathVariable Long ticketId,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        log.info("REST request to get comments for ticket: {} (role={})", ticketId, roleHeader);
        List<CommentDTO> comments = ticketService.getTicketComments(ticketId, roleHeader);
        return ResponseEntity.ok(comments);
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "Delete comment")
    public ResponseEntity<Void> deleteComment(@PathVariable Long commentId) {
        log.info("REST request to delete comment: {}", commentId);
        ticketService.deleteComment(commentId);
        return ResponseEntity.noContent().build();
    }

    // ----------------------------------------------------------------
    //  Targeted PATCH operations (HESK Priority 3.1)
    // ----------------------------------------------------------------

    @PatchMapping("/{id}/assign")
    @Operation(summary = "Assign ticket to a technician",
            description = "Body: {\"assignedTo\": 7}. Pass null to unassign. Staff-only.")
    public ResponseEntity<TicketDTO> assignTicket(
            @PathVariable Long id,
            @RequestBody AssignmentRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(ticketService.assignTicket(id, body.getAssignedTo(), role));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change ticket status",
            description = "Body: {\"status\": \"IN_PROGRESS\"}. Staff-only; technicians scoped to assigned tickets.")
    public ResponseEntity<TicketDTO> changeStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        return ResponseEntity.ok(
            ticketService.changeStatus(id, body.getStatus(), role, parseUserId(userIdHeader)));
    }

    @PatchMapping("/{id}/priority")
    @Operation(summary = "Change ticket priority",
            description = "Body: {\"priority\": \"HIGH\"}. Staff-only; technicians scoped to assigned tickets.")
    public ResponseEntity<TicketDTO> changePriority(
            @PathVariable Long id,
            @RequestBody PriorityUpdateRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        return ResponseEntity.ok(
            ticketService.changePriority(id, body.getPriority(), role, parseUserId(userIdHeader)));
    }

    @PatchMapping("/{id}/reopen")
    @Operation(summary = "Reopen a RESOLVED or CLOSED ticket",
            description = "Allowed for the original creator OR any staff member.")
    public ResponseEntity<TicketDTO> reopen(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        return ResponseEntity.ok(
            ticketService.reopenTicket(id, role, parseUserId(userIdHeader)));
    }

    @PatchMapping("/{id}/link-asset")
    @Operation(summary = "Link or unlink an asset",
            description = "Body: {\"assetId\": 12}. Pass null to unlink. Staff-only.")
    public ResponseEntity<TicketDTO> linkAsset(
            @PathVariable Long id,
            @RequestBody LinkAssetRequest body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        return ResponseEntity.ok(ticketService.linkAsset(id, body.getAssetId(), role));
    }

    private static Long parseUserId(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return Long.parseLong(header.trim());
    }

    // ----------------------------------------------------------------
    //  Attachments (Bug 3) — multi-file uploads per ticket
    // ----------------------------------------------------------------

    /**
     * Upload a file attached to the given ticket. Permission rules:
     *   - ADMIN: any ticket
     *   - TECHNICIAN: only tickets assigned to them
     *   - EMPLOYEE: only tickets they created
     * Browser sends one file at a time; the frontend loops for multi-upload.
     */
    @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file to a ticket")
    public ResponseEntity<AttachmentDTO> uploadAttachment(
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) throws java.io.IOException {
        Long uid = parseUserId(userIdHeader);
        if (uid == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        var t = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new com.helpdesk.ticket.exception.TicketNotFoundException(
                "Ticket not found with ID: " + ticketId));
        boolean admin = TicketService.isAdmin(roleHeader);
        boolean technician = "TECHNICIAN".equalsIgnoreCase(roleHeader);
        boolean owner = uid.equals(t.getCreatedBy());
        boolean assigned = uid.equals(t.getAssignedTo());
        if (!admin && !owner && !(technician && assigned)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        long existing = attachmentRepository.countByTicketId(ticketId);
        var stored = attachmentStorage.store(ticketId, file, existing);
        Attachment a = Attachment.builder()
            .ticketId(ticketId)
            .uploadedBy(uid)
            .originalFilename(file.getOriginalFilename() == null ? stored.storedFilename() : file.getOriginalFilename())
            .storedFilename(stored.storedFilename())
            .contentType(file.getContentType())
            .sizeBytes(file.getSize())
            .build();
        Attachment saved = attachmentRepository.save(a);
        return ResponseEntity.status(HttpStatus.CREATED).body(AttachmentDTO.fromEntity(saved));
    }

    @GetMapping("/{ticketId}/attachments")
    @Operation(summary = "List attachments for a ticket")
    public ResponseEntity<java.util.List<AttachmentDTO>> listAttachments(@PathVariable Long ticketId) {
        if (!ticketRepository.existsById(ticketId)) {
            return ResponseEntity.notFound().build();
        }
        var list = attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId)
            .stream().map(AttachmentDTO::fromEntity).toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Stream the attachment bytes. Public-route at the gateway so plain
     * <img src=> works in the browser (the attachment id is a numeric
     * surrogate; not as guessable as a filename but still an internal IT tool).
     */
    @GetMapping("/attachments/{attachmentId}/download")
    @Operation(summary = "Download an attachment by id")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long attachmentId) {
        Attachment a = attachmentRepository.findById(attachmentId).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        Resource res = attachmentStorage.load(a.getStoredFilename());
        if (res == null) return ResponseEntity.notFound().build();
        MediaType ct = a.getContentType() != null
            ? MediaType.parseMediaType(a.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
            .contentType(ct)
            .header("Cache-Control", "private, max-age=3600")
            .header("Content-Disposition", "inline; filename=\"" + a.getOriginalFilename().replace("\"", "") + "\"")
            .body(res);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @Operation(summary = "Delete an attachment (uploader or admin)")
    public ResponseEntity<Void> deleteAttachment(
            @PathVariable Long attachmentId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        Long uid = parseUserId(userIdHeader);
        Attachment a = attachmentRepository.findById(attachmentId).orElse(null);
        if (a == null) return ResponseEntity.notFound().build();
        boolean admin = TicketService.isAdmin(roleHeader);
        if (!admin && (uid == null || !uid.equals(a.getUploadedBy()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        attachmentStorage.delete(a.getStoredFilename());
        attachmentRepository.delete(a);
        return ResponseEntity.noContent().build();
    }
}
