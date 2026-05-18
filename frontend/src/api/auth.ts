import client from './client'
import { AuthResponse } from '../types'

export interface LoginRequest {
  email: string
  password: string
}

export const authAPI = {
  login: (data: LoginRequest) =>
    client.post<AuthResponse>('/auth/login', data),

  refreshToken: (refreshToken: string) =>
    client.post<AuthResponse>('/auth/refresh', { refreshToken }),

  validateToken: (token: string) =>
    client.get<boolean>('/auth/validate', { params: { token } }),

  health: () =>
    client.get('/auth/health'),

  /**
   * Admin-only: reset another user's password. The gateway forwards the
   * caller's role; non-ADMIN gets 401/403 from auth-service.
   */
  adminResetPassword: (userId: number, newPassword: string) =>
    client.patch<void>(`/auth/admin/reset-password/${userId}`, { newPassword }),

  /**
   * Creates auth + user_db row (same id). ADMIN only.
   */
  adminProvisionUser: (data: AdminProvisionRequest) =>
    client.post<AdminProvisionResponse>('/auth/admin/users', data),

  /** User exists in user_db but not auth_db — creates auth row with same id + password. */
  enableDirectoryLogin: (email: string, newPassword: string) =>
    client.post<void>('/auth/admin/enable-directory-login', { email, newPassword }),
}

export interface AdminProvisionRequest {
  email: string
  firstName: string
  lastName: string
  password: string
  role: 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'
  department?: string
  phone?: string
}

export interface AdminProvisionResponse {
  userId: number
  email: string
  firstName: string
  lastName: string
  role: string
}
