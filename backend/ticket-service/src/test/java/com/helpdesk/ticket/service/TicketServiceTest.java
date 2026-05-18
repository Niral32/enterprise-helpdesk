package com.helpdesk.ticket.service;

import com.helpdesk.ticket.client.DirectoryClient;
import com.helpdesk.ticket.client.NotificationClient;
import com.helpdesk.ticket.client.NotificationClient.NotificationType;
import com.helpdesk.ticket.dto.CommentDTO;
import com.helpdesk.ticket.dto.TicketDTO;
import com.helpdesk.ticket.entity.Attachment;
import com.helpdesk.ticket.entity.Comment;
import com.helpdesk.ticket.entity.Ticket;
import com.helpdesk.ticket.exception.TicketAccessDeniedException;
import com.helpdesk.ticket.exception.TicketNotFoundException;
import com.helpdesk.ticket.repository.AttachmentRepository;
import com.helpdesk.ticket.repository.CommentRepository;
import com.helpdesk.ticket.repository.TicketRepository;
import com.helpdesk.ticket.storage.AttachmentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TicketService unit tests.
 *
 * Heavy on permission-matrix coverage because that's where the bugs live:
 *   - admin can do anything
 *   - technician can do things on tickets assigned to them
 *   - employee can only manipulate their own tickets, and only in pre-terminal states
 */
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock private TicketRepository ticketRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private AttachmentRepository attachmentRepository;
    @Mock private AttachmentStorageService attachmentStorage;
    @Mock private DirectoryClient directoryClient;
    @Mock private NotificationClient notificationClient;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
            ticketRepository,
            commentRepository,
            attachmentRepository,
            attachmentStorage,
            directoryClient,
            notificationClient
        );
        // enrichDto calls directoryClient on every result — return empty
        // so we don't have to stub it in every test.
        lenient().when(directoryClient.getUser(any())).thenReturn(Optional.empty());
        lenient().when(directoryClient.getAsset(any())).thenReturn(Optional.empty());
    }

    // ────────────────────────────────────────────────────────────────────
    //  createTicket
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createTicket: status starts at NEW and a TKT-NNNNNN number is generated")
    void createTicket_generatesNumber() {
        TicketDTO dto = newDto("Printer broken");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(7L);
            return t;
        });

        TicketDTO out = ticketService.createTicket(dto, 100L);

        assertThat(out.getStatus()).isEqualTo("NEW");
        assertThat(out.getTicketNumber()).isEqualTo("TKT-000007");
        assertThat(out.getCreatedBy()).isEqualTo(100L);
    }

    @Test
    @DisplayName("createTicket: fires a TICKET_CREATED notification to the creator")
    void createTicket_notifiesCreator() {
        TicketDTO dto = newDto("Mouse broken");
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });

        ticketService.createTicket(dto, 200L);

        verify(notificationClient).notify(
            eq(200L),
            any(),
            any(),
            eq(NotificationType.TICKET_CREATED),
            eq(1L));
    }

    @Test
    @DisplayName("createTicket: throws when no createdBy can be determined")
    void createTicket_requiresCreator() {
        TicketDTO dto = newDto("X");
        assertThatThrownBy(() -> ticketService.createTicket(dto, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  assignTicket
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("assignTicket: NEW → ASSIGNED when assignedTo set")
    void assignTicket_movesNewToAssigned() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.NEW);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.assignTicket(5L, 77L, "ADMIN");

        assertThat(t.getStatus()).isEqualTo(Ticket.TicketStatus.ASSIGNED);
        assertThat(t.getAssignedTo()).isEqualTo(77L);
    }

    @Test
    @DisplayName("assignTicket: status NOT changed when ticket already IN_PROGRESS")
    void assignTicket_preservesInProgress() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.assignTicket(5L, 77L, "ADMIN");

        assertThat(t.getStatus()).isEqualTo(Ticket.TicketStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("assignTicket: rejects non-staff requester")
    void assignTicket_rejectsEmployee() {
        assertThatThrownBy(() -> ticketService.assignTicket(5L, 77L, "EMPLOYEE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TECHNICIAN or ADMIN");
    }

    @Test
    @DisplayName("assignTicket: notifies the new assignee")
    void assignTicket_notifiesAssignee() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.NEW);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.assignTicket(5L, 77L, "ADMIN");

        verify(notificationClient).notify(
            eq(77L), any(), any(), eq(NotificationType.TICKET_ASSIGNED), eq(5L));
    }

    // ────────────────────────────────────────────────────────────────────
    //  changeStatus
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeStatus: ADMIN may move any ticket through any state")
    void changeStatus_adminCanDoAnything() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.NEW);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.changeStatus(5L, "RESOLVED", "ADMIN", 1L);

        assertThat(t.getStatus()).isEqualTo(Ticket.TicketStatus.RESOLVED);
        assertThat(t.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus: TECHNICIAN cannot change status of a ticket not assigned to them")
    void changeStatus_techScopedToOwnTickets() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        t.setAssignedTo(999L); // someone else
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() ->
            ticketService.changeStatus(5L, "IN_PROGRESS", "TECHNICIAN", 77L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assigned to them");
    }

    @Test
    @DisplayName("changeStatus: resolvedAt is stamped only the first time RESOLVED is hit")
    void changeStatus_resolvedAtIdempotent() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.IN_PROGRESS);
        t.setResolvedAt(java.time.LocalDateTime.now().minusDays(1));
        var original = t.getResolvedAt();
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.changeStatus(5L, "RESOLVED", "ADMIN", 1L);

        assertThat(t.getResolvedAt()).isEqualTo(original); // unchanged
    }

    // ────────────────────────────────────────────────────────────────────
    //  cancelTicket
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelTicket: owner can cancel their own ticket")
    void cancelTicket_ownerCan() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.cancelTicket(5L, "EMPLOYEE", 100L);

        assertThat(t.getStatus()).isEqualTo(Ticket.TicketStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelTicket: stranger cannot cancel someone else's ticket")
    void cancelTicket_strangerCannot() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.cancelTicket(5L, "EMPLOYEE", 999L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cancelTicket: cannot cancel a RESOLVED ticket")
    void cancelTicket_cannotCancelResolved() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.cancelTicket(5L, "EMPLOYEE", 100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resolved or closed");
    }

    @Test
    @DisplayName("cancelTicket: already-cancelled ticket returns OK (idempotent)")
    void cancelTicket_idempotent() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.CANCELLED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        TicketDTO out = ticketService.cancelTicket(5L, "EMPLOYEE", 100L);

        assertThat(out.getStatus()).isEqualTo("CANCELLED");
        verify(ticketRepository, never()).save(any());
    }

    // ────────────────────────────────────────────────────────────────────
    //  deleteTicket — admin-vs-technician policy
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteTicket: ADMIN may delete tickets in any state")
    void deleteTicket_adminAnyState() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(commentRepository.findByTicketId(5L)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

        ticketService.deleteTicket(5L, "ADMIN", 1L);

        verify(ticketRepository).deleteById(5L);
    }

    @Test
    @DisplayName("deleteTicket: TECHNICIAN may delete only their assigned ticket in a terminal state")
    void deleteTicket_techTerminalOnly() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        t.setAssignedTo(77L);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(commentRepository.findByTicketId(5L)).thenReturn(List.of());
        when(attachmentRepository.findByTicketIdOrderByCreatedAtAsc(5L)).thenReturn(List.of());

        ticketService.deleteTicket(5L, "TECHNICIAN", 77L);

        verify(ticketRepository).deleteById(5L);
    }

    @Test
    @DisplayName("deleteTicket: TECHNICIAN refused on an active ticket assigned to them")
    void deleteTicket_techActiveRefused() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.IN_PROGRESS);
        t.setAssignedTo(77L);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.deleteTicket(5L, "TECHNICIAN", 77L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("resolved, closed, or cancelled");
    }

    @Test
    @DisplayName("deleteTicket: TECHNICIAN refused on a ticket not assigned to them")
    void deleteTicket_techNotAssigned() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        t.setAssignedTo(999L);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.deleteTicket(5L, "TECHNICIAN", 77L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteTicket: EMPLOYEE always refused")
    void deleteTicket_employeeRefused() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.deleteTicket(5L, "EMPLOYEE", 100L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("deleteTicket: cleans up comments and attachment files")
    void deleteTicket_cleansChildren() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.CLOSED);
        Comment c = Comment.builder().id(1L).ticketId(5L).userId(100L).commentText("hi").build();
        Attachment a = Attachment.builder()
            .id(1L).ticketId(5L).uploadedBy(100L)
            .originalFilename("x.png").storedFilename("stored-x.png")
            .sizeBytes(100L).build();

        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(commentRepository.findByTicketId(5L)).thenReturn(List.of(c));
        when(attachmentRepository.findByTicketIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(a));

        ticketService.deleteTicket(5L, "ADMIN", 1L);

        verify(commentRepository).deleteAll(List.of(c));
        verify(attachmentStorage).delete("stored-x.png");
        verify(attachmentRepository).deleteAll(List.of(a));
        verify(ticketRepository).deleteById(5L);
    }

    // ────────────────────────────────────────────────────────────────────
    //  reopenTicket
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reopenTicket: owner can reopen their RESOLVED ticket")
    void reopen_ownerHappy() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        t.setResolvedAt(java.time.LocalDateTime.now());
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.reopenTicket(5L, "EMPLOYEE", 100L);

        assertThat(t.getStatus()).isEqualTo(Ticket.TicketStatus.REOPENED);
        assertThat(t.getResolvedAt()).isNull();
        assertThat(t.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("reopenTicket: only RESOLVED or CLOSED tickets can be reopened")
    void reopen_onlyTerminal() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.IN_PROGRESS);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.reopenTicket(5L, "ADMIN", 1L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reopenTicket: stranger refused")
    void reopen_strangerRefused() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.RESOLVED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.reopenTicket(5L, "EMPLOYEE", 999L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  linkAsset
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("linkAsset: staff sets the linkedAssetId")
    void linkAsset_setsId() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        ticketService.linkAsset(5L, 42L, "ADMIN");

        assertThat(t.getLinkedAssetId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("linkAsset: employee refused")
    void linkAsset_employeeRefused() {
        assertThatThrownBy(() -> ticketService.linkAsset(5L, 42L, "EMPLOYEE"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  getTicketForViewer — read-access policy
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getTicketForViewer: ADMIN reads any ticket")
    void getForViewer_admin() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        TicketDTO out = ticketService.getTicketForViewer(5L, "ADMIN", 1L);

        assertThat(out.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getTicketForViewer: EMPLOYEE blocked from viewing other people's tickets")
    void getForViewer_employeeOther() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.getTicketForViewer(5L, "EMPLOYEE", 999L))
            .isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    @DisplayName("getTicketForViewer: TECHNICIAN blocked from a ticket not assigned to them")
    void getForViewer_techNotAssigned() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        t.setAssignedTo(999L);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> ticketService.getTicketForViewer(5L, "TECHNICIAN", 77L))
            .isInstanceOf(TicketAccessDeniedException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  addComment — internal-vs-public + notification routing
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addComment: public reply by owner notifies the assignee")
    void addComment_publicReplyByOwnerNotifiesAssignee() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        t.setAssignedTo(77L);
        when(ticketRepository.existsById(5L)).thenReturn(true);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CommentDTO dto = new CommentDTO();
        dto.setCommentText("Any update?");
        dto.setIsInternal(false);
        ticketService.addComment(5L, dto, 100L);

        verify(notificationClient).notify(eq(77L), any(), any(), eq(NotificationType.COMMENT_ADDED), eq(5L));
    }

    @Test
    @DisplayName("addComment: throws when the ticket is missing")
    void addComment_missingTicket() {
        when(ticketRepository.existsById(99L)).thenReturn(false);

        CommentDTO dto = new CommentDTO();
        dto.setCommentText("hi");
        assertThatThrownBy(() -> ticketService.addComment(99L, dto, 1L))
            .isInstanceOf(TicketNotFoundException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  updateTicketForCaller — role-scoped patch
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateTicketForCaller: ADMIN can patch any field on any ticket")
    void updateForCaller_admin() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketDTO patch = new TicketDTO();
        patch.setTitle("New title");
        patch.setStatus("IN_PROGRESS");
        TicketDTO out = ticketService.updateTicketForCaller(5L, patch, "ADMIN", 1L);

        assertThat(out.getTitle()).isEqualTo("New title");
        assertThat(out.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("updateTicketForCaller: owner cannot self-promote status / assign / linked asset")
    void updateForCaller_ownerCannotEscalate() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        TicketDTO patch = new TicketDTO();
        patch.setTitle("ok");
        patch.setStatus("RESOLVED");   // should be stripped
        patch.setAssignedTo(77L);       // should be stripped
        patch.setLinkedAssetId(42L);    // should be stripped

        ticketService.updateTicketForCaller(5L, patch, "EMPLOYEE", 100L);

        ArgumentCaptor<Ticket> saved = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(Ticket.TicketStatus.OPEN);
        assertThat(saved.getValue().getAssignedTo()).isNull();
        assertThat(saved.getValue().getLinkedAssetId()).isNull();
    }

    @Test
    @DisplayName("updateTicketForCaller: owner refused once ticket is CLOSED")
    void updateForCaller_ownerBlockedOnTerminal() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.CLOSED);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        TicketDTO patch = new TicketDTO();
        patch.setTitle("x");
        assertThatThrownBy(() ->
            ticketService.updateTicketForCaller(5L, patch, "EMPLOYEE", 100L))
            .isInstanceOf(TicketAccessDeniedException.class);
    }

    @Test
    @DisplayName("updateTicketForCaller: stranger refused")
    void updateForCaller_strangerRefused() {
        Ticket t = newTicket(5L, 100L, Ticket.TicketStatus.OPEN);
        when(ticketRepository.findById(5L)).thenReturn(Optional.of(t));

        TicketDTO patch = new TicketDTO();
        patch.setTitle("nope");
        assertThatThrownBy(() ->
            ticketService.updateTicketForCaller(5L, patch, "EMPLOYEE", 999L))
            .isInstanceOf(TicketAccessDeniedException.class);
    }

    // ────────────────────────────────────────────────────────────────────
    //  helpers
    // ────────────────────────────────────────────────────────────────────

    private static TicketDTO newDto(String title) {
        TicketDTO d = new TicketDTO();
        d.setTitle(title);
        d.setDescription("x");
        d.setPriority("MEDIUM");
        d.setCategory("Hardware");
        return d;
    }

    private static Ticket newTicket(Long id, Long createdBy, Ticket.TicketStatus status) {
        return Ticket.builder()
            .id(id)
            .ticketNumber(String.format("TKT-%06d", id))
            .title("t").description("d")
            .priority(Ticket.TicketPriority.MEDIUM)
            .category("Hardware")
            .status(status)
            .createdBy(createdBy)
            .build();
    }
}
