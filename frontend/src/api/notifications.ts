import client from './client'
import { Notification } from '../types'

/** Uses `/api/notifications/me*` — gateway forwards JWT-derived X-User-Id. */
export const notificationAPI = {
  listMine: () => client.get<Notification[]>('/notifications/me'),

  listUnread: () => client.get<Notification[]>('/notifications/me/unread'),

  unreadCount: () => client.get<{ unreadCount: number }>('/notifications/me/unread-count'),

  markAllRead: () => client.put<void>('/notifications/me/read-all'),

  markRead: (id: number) => client.put<Notification>(`/notifications/${id}/read`),
}
