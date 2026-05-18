import React, { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Menu, Bell } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'
import { notificationAPI } from '../../api/notifications'
import type { Notification } from '../../types'
import Avatar from '../Common/Avatar'

interface HeaderProps {
  onSidebarToggle: () => void
}

const Header: React.FC<HeaderProps> = ({ onSidebarToggle }) => {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Notification[]>([])
  const [unread, setUnread] = useState(0)
  const [loadingBell, setLoadingBell] = useState(false)
  const panelRef = useRef<HTMLDivElement>(null)

  const loadNotifs = async () => {
    try {
      const [listRes, countRes] = await Promise.all([
        notificationAPI.listMine(),
        notificationAPI.unreadCount(),
      ])
      setItems((listRes.data ?? []).slice(0, 20))
      setUnread(countRes.data?.unreadCount ?? 0)
    } catch {
      setItems([])
      setUnread(0)
    }
  }

  useEffect(() => {
    void loadNotifs()
    const t = window.setInterval(() => void loadNotifs(), 60000)
    return () => window.clearInterval(t)
  }, [user?.id])

  useEffect(() => {
    const onDoc = (e: MouseEvent) => {
      if (!panelRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDoc)
    return () => document.removeEventListener('mousedown', onDoc)
  }, [])

  const onBell = async () => {
    if (!open) {
      setLoadingBell(true)
      await loadNotifs()
      setLoadingBell(false)
    }
    setOpen((o) => !o)
  }

  const markOneRead = async (n: Notification, e: React.MouseEvent) => {
    e.stopPropagation()
    try {
      await notificationAPI.markRead(n.id)
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, isRead: true } : x)))
      setUnread((u) => Math.max(0, u - 1))
    } catch {
      /* ignore */
    }
  }

  const markAllRead = async () => {
    try {
      await notificationAPI.markAllRead()
      setItems((prev) => prev.map((x) => ({ ...x, isRead: true })))
      setUnread(0)
    } catch {
      /* ignore */
    }
  }

  const goNotification = (n: Notification) => {
    if (n.entityType === 'TICKET' && n.entityId != null) {
      navigate(`/tickets/${n.entityId}`)
    } else {
      navigate('/tickets')
    }
    setOpen(false)
    if (!n.isRead) void notificationAPI.markRead(n.id)
  }

  return (
    <header className="bg-white border-b border-gray-200 px-4 md:px-6 py-4 flex items-center justify-between">
      <div className="flex items-center space-x-4">
        <button
          onClick={onSidebarToggle}
          className="p-2 hover:bg-gray-100 rounded-lg transition-colors"
        >
          <Menu className="h-6 w-6 text-gray-600" />
        </button>
        <h1 className="text-2xl font-bold text-gray-800">Help Desk System</h1>
      </div>

      <div className="flex items-center space-x-4">
        <div className="relative" ref={panelRef}>
          <button
            type="button"
            onClick={() => void onBell()}
            disabled={loadingBell}
            className="relative p-2 hover:bg-gray-100 rounded-lg transition-colors"
            aria-label="Notifications"
          >
            <Bell className="h-6 w-6 text-gray-600" />
            {unread > 0 && (
              <span className="absolute top-1 right-1 min-w-[1.1rem] h-[1.1rem] px-1 flex items-center justify-center text-[10px] font-bold text-white bg-red-500 rounded-full">
                {unread > 99 ? '99+' : unread}
              </span>
            )}
          </button>

          {open && (
            <div className="absolute right-0 mt-2 w-96 max-h-[min(70vh,420px)] overflow-y-auto bg-white rounded-lg shadow-xl border border-gray-200 z-50">
              <div className="flex items-center justify-between px-4 py-2 border-b border-gray-100">
                <span className="text-sm font-semibold text-gray-800">Notifications</span>
                {unread > 0 && (
                  <button
                    type="button"
                    onClick={() => void markAllRead()}
                    className="text-xs text-primary-600 hover:underline"
                  >
                    Mark all read
                  </button>
                )}
              </div>
              {items.length === 0 ? (
                <p className="px-4 py-6 text-sm text-gray-500 text-center">No notifications yet.</p>
              ) : (
                <ul className="divide-y divide-gray-100">
                  {items.map((n) => (
                    <li key={n.id}>
                      <button
                        type="button"
                        className={`w-full text-left px-4 py-3 hover:bg-gray-50 flex gap-2 ${!n.isRead ? 'bg-primary-50/40' : ''}`}
                        onClick={() => goNotification(n)}
                      >
                        <span
                          className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.isRead ? 'bg-gray-200' : 'bg-primary-500'}`}
                        />
                        <span className="min-w-0">
                          <span className="block text-sm font-medium text-gray-900 truncate">{n.title}</span>
                          <span className="block text-xs text-gray-600 line-clamp-2">{n.message}</span>
                          <span className="block text-[10px] text-gray-400 mt-1">
                            {new Date(n.createdAt).toLocaleString()}
                          </span>
                        </span>
                        {!n.isRead && (
                          <button
                            type="button"
                            className="text-xs text-gray-500 hover:text-primary-600 shrink-0"
                            onClick={(e) => markOneRead(n, e)}
                          >
                            Read
                          </button>
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <div className="px-4 py-2 border-t border-gray-100 text-right">
                <Link to="/tickets" className="text-xs text-primary-600 hover:underline" onClick={() => setOpen(false)}>
                  Go to tickets
                </Link>
              </div>
            </div>
          )}
        </div>

        <div className="flex items-center space-x-3 pl-4 border-l border-gray-200">
          <div className="text-right">
            <p className="text-sm font-medium text-gray-800">
              {user?.firstName} {user?.lastName}
            </p>
            <p className="text-xs text-gray-500">{user?.role}</p>
          </div>
          <Avatar
            firstName={user?.firstName}
            lastName={user?.lastName}
            imageUrl={user?.profileImageUrl}
            size={40}
          />
        </div>
      </div>
    </header>
  )
}

export default Header
