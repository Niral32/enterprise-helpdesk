import client from './client'
import { User } from '../types'
import type { AdminProvisionRequest, AdminProvisionResponse } from './auth'

export interface CreateUserRequest {
  email: string
  firstName: string
  lastName: string
  password: string
  role: 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'
  department?: string
  phone?: string
}

export interface UpdateUserRequest {
  firstName?: string
  lastName?: string
  department?: string
  phone?: string
  role?: 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'
  status?: 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'PENDING'
}

export const userAPI = {
  getAll: (params?: Record<string, unknown>) =>
    client.get<User[]>('/users', { params }),

  getById: (id: number) =>
    client.get<User>(`/users/${id}`),

  create: (data: CreateUserRequest) =>
    client.post<User>('/users', data),

  update: (id: number, data: UpdateUserRequest) =>
    client.put<User>(`/users/${id}`, data),

  delete: (id: number) =>
    client.delete(`/users/${id}`),

  /** Backend expects query param name `query`, not `q`. */
  search: (query: string) =>
    client.get<User[]>('/users/search', { params: { query } }),

  /** Requires JWT via gateway; uses X-User-Id injected server-side. */
  getProfile: () =>
    client.get<User>('/users/me'),

  updateProfile: (data: UpdateUserRequest) =>
    client.put<User>('/users/me', data),

  // -------------------------------------------------------------------
  //  Admin user-management — backed by /api/users admin endpoints.
  //  Role authorization happens server-side via X-User-Role header
  //  (forwarded by the gateway from the JWT). The 403 responses surface
  //  here as Axios errors so the UI can show a friendly message.
  // -------------------------------------------------------------------

  /** Creates credentials in auth_db and profile in user_db via auth-service (ADMIN only). */
  adminCreate: (data: CreateUserRequest) =>
    client.post<AdminProvisionResponse>('/auth/admin/users', data as AdminProvisionRequest),

  block: (id: number) =>
    client.patch<User>(`/users/${id}/block`),

  unblock: (id: number) =>
    client.patch<User>(`/users/${id}/unblock`),

  deactivate: (id: number) =>
    client.patch<User>(`/users/${id}/deactivate`),

  changeRole: (id: number, role: 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE') =>
    client.patch<User>(`/users/${id}/role`, { role }),

  changeEmail: (id: number, email: string) =>
    client.patch<User>(`/users/${id}/email`, { email }),

  /** ADMIN-only: list technicians for assignment dropdowns. */
  getTechnicians: () => client.get<User[]>('/users/role/TECHNICIAN'),

  /** Upload (or replace) the current user's profile photo. */
  uploadMyPhoto: (file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    // No explicit Content-Type — the client interceptor strips the JSON
    // default and the browser sets multipart/form-data with a boundary.
    return client.post<User>('/users/me/photo', fd)
  },

  removeMyPhoto: () => client.delete<User>('/users/me/photo'),
}
