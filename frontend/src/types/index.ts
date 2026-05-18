// User Types
export interface User {
  id: number
  email: string
  firstName: string
  lastName: string
  department?: string
  phone?: string
  role: 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'
  status: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'PENDING'
  /** Relative URL (e.g. /api/users/avatars/user-7-uuid.jpg). Null = use initials avatar. */
  profileImageUrl?: string | null
  createdAt: string
}

// Ticket Types
export type TicketStatus =
  | 'NEW'
  | 'OPEN'
  | 'ASSIGNED'
  | 'IN_PROGRESS'
  | 'WAITING_FOR_USER'
  | 'WAITING_FOR_VENDOR'
  | 'ON_HOLD'
  | 'RESOLVED'
  | 'CLOSED'
  | 'REOPENED'
  | 'CANCELLED'

export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface Ticket {
  id: number
  /** Human-readable identifier from the server, e.g. "TKT-000142". */
  ticketNumber?: string
  title: string
  description: string
  priority: TicketPriority
  category: string
  status: TicketStatus
  createdBy: number
  assignedTo?: number
  /** ID of an Asset (in asset-service) related to this ticket, if any. */
  linkedAssetId?: number
  /** Enriched by ticket-service (optional). */
  createdByName?: string
  createdByEmail?: string
  createdByDepartment?: string
  assignedToName?: string
  assignedToEmail?: string
  linkedAssetLabel?: string
  // ----- Optional device-location fields supplied at ticket creation -------
  /** Building or campus block where the failing device lives. */
  building?: string
  /** Department location of the device (not necessarily the requester's dept). */
  locationDepartment?: string
  /** Room/office number. */
  roomNumber?: string
  /** Free-text directions ("behind printer", "row 3 left desk"…). */
  locationNotes?: string
  createdAt: string
  updatedAt: string
  resolvedAt?: string
  closedAt?: string
}

export interface Comment {
  id: number
  ticketId: number
  userId: number
  commentText: string
  /** True for technician/admin internal notes; false (or absent) for public replies. */
  isInternal?: boolean
  authorName?: string
  authorRole?: string
  authorAvatarUrl?: string | null
  createdAt: string
  updatedAt: string
}

// Asset Types
/** Matches asset-service / asset_db (enum AssetStatus). */
export type AssetStatus =
  | 'AVAILABLE'
  | 'ASSIGNED'
  | 'IN_REPAIR'
  | 'RETIRED'
  | 'LOST'
  | 'IN_USE'
  | 'MAINTENANCE'

export interface Asset {
  id: number
  /** Backend field is `name` (display as asset label). */
  name: string
  assetType: string
  serialNumber?: string
  description?: string
  assignedTo?: number
  location?: string
  purchaseDate?: string
  warrantyExpiry?: string
  cost?: number
  vendor?: string
  status: AssetStatus
  createdAt: string
  updatedAt: string
}

// Ticket attachments
export interface Attachment {
  id: number
  ticketId: number
  uploadedBy: number
  originalFilename: string
  contentType?: string
  sizeBytes: number
  downloadUrl: string
  previewUrl: string
  createdAt: string
}

// Notification Types
export interface Notification {
  id: number
  userId: number
  title: string
  message: string
  /** Backend JSON field name */
  type: string
  entityType?: string
  entityId?: number
  isRead: boolean
  createdAt: string
  readAt?: string
}

// Auth Types
export interface AuthResponse {
  token: string
  refreshToken: string
  type: string
  userId: number
  email: string
  firstName: string
  lastName: string
  role: string
  profileImageUrl?: string | null
  expiresIn: number
}

// Pagination
export interface PaginationParams {
  page?: number
  pageSize?: number
  sortBy?: string
  sortOrder?: 'asc' | 'desc'
}

// API Response
export interface ApiResponse<T> {
  data: T
  message?: string
  status: number
  timestamp?: string
}
