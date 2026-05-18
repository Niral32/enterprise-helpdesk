import client from './client'
import { Ticket, Comment, TicketStatus, TicketPriority, Attachment } from '../types'

export interface CreateTicketRequest {
  title: string
  description: string
  priority: TicketPriority
  category: string
  /** Optional asset link at creation time (admin-created tickets, etc). */
  linkedAssetId?: number
  // Optional location of the failing device
  building?: string
  locationDepartment?: string
  roomNumber?: string
  locationNotes?: string
}

export interface UpdateTicketRequest {
  title?: string
  description?: string
  priority?: TicketPriority
  status?: TicketStatus
  category?: string
  assignedTo?: number
  linkedAssetId?: number | null
  building?: string
  locationDepartment?: string
  roomNumber?: string
  locationNotes?: string
}

export interface CreateCommentRequest {
  commentText: string
}

export const ticketAPI = {
  // ── Existing routes (kept for backwards compat) ─────────────────────
  getAll: (params?: any) =>
    client.get<Ticket[]>('/tickets', { params }),

  getById: (id: number) =>
    client.get<Ticket>(`/tickets/${id}`),

  create: (data: CreateTicketRequest) =>
    client.post<Ticket>('/tickets', data),

  update: (id: number, data: UpdateTicketRequest) =>
    client.put<Ticket>(`/tickets/${id}`, data),

  delete: (id: number) =>
    client.delete(`/tickets/${id}`),

  /** Public reply — visible to the ticket creator. */
  addComment: (ticketId: number, data: CreateCommentRequest) =>
    client.post<Comment>(`/tickets/${ticketId}/comments`, data),

  getComments: (ticketId: number) =>
    client.get<Comment[]>(`/tickets/${ticketId}/comments`),

  // ── Role-scoped views (HESK Priority 3.1) ───────────────────────────
  /** USER: tickets I created. */
  getMine: () =>
    client.get<Ticket[]>('/tickets/my'),

  /** TECHNICIAN: tickets assigned to me. */
  getMyQueue: () =>
    client.get<Ticket[]>('/tickets/technician/queue'),

  /** ADMIN: all tickets. Server returns 403 for non-admin. */
  getAllForAdmin: () =>
    client.get<Ticket[]>('/tickets/admin/all'),

  // ── Targeted PATCH operations ───────────────────────────────────────
  assign: (ticketId: number, assignedTo: number | null) =>
    client.patch<Ticket>(`/tickets/${ticketId}/assign`, { assignedTo }),

  changeStatus: (ticketId: number, status: TicketStatus) =>
    client.patch<Ticket>(`/tickets/${ticketId}/status`, { status }),

  changePriority: (ticketId: number, priority: TicketPriority) =>
    client.patch<Ticket>(`/tickets/${ticketId}/priority`, { priority }),

  reopen: (ticketId: number) =>
    client.patch<Ticket>(`/tickets/${ticketId}/reopen`),

  /** Owner or admin — sets status CANCELLED */
  cancel: (ticketId: number) => client.patch<Ticket>(`/tickets/${ticketId}/cancel`),

  linkAsset: (ticketId: number, assetId: number | null) =>
    client.patch<Ticket>(`/tickets/${ticketId}/link-asset`, { assetId }),

  /** Staff-only: add an internal note (hidden from end-users). */
  addInternalNote: (ticketId: number, data: CreateCommentRequest) =>
    client.post<Comment>(`/tickets/${ticketId}/internal-notes`, data),

  // ── Attachments (Bug 3) ─────────────────────────────────────────────
  listAttachments: (ticketId: number) =>
    client.get<Attachment[]>(`/tickets/${ticketId}/attachments`),

  uploadAttachment: (ticketId: number, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    // No explicit Content-Type — the client interceptor strips the JSON
    // default and the browser fills in the multipart boundary.
    return client.post<Attachment>(`/tickets/${ticketId}/attachments`, fd)
  },

  deleteAttachment: (attachmentId: number) =>
    client.delete<void>(`/tickets/attachments/${attachmentId}`),
}
