import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { CheckCircle2, Clock, Inbox, Zap, RefreshCw } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { ticketAPI } from '../api/tickets'
import { Ticket as TicketModel } from '../types'
import { useNotification } from '../context/NotificationContext'

const TechnicianDashboard: React.FC = () => {
  const { user } = useAuth()
  const { showToast } = useNotification()
  const location = useLocation()
  const [loading, setLoading] = useState(true)
  const [tickets, setTickets] = useState<TicketModel[]>([])
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const tq = await ticketAPI.getMyQueue()
      const list = tq.data ?? []
      setTickets(list)
    } catch {
      setLoadError('Could not load your queue. Ensure you are signed in as a technician.')
      setTickets([])
      showToast('Could not load technician dashboard', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    load()
  }, [load, location.key])

  const myStats = useMemo(() => {
    const assignedToMe = tickets.length
    const inProgress = tickets.filter((t) => t.status === 'IN_PROGRESS').length
    const resolvedThisWeek = tickets.filter((t) => {
      if (t.status !== 'RESOLVED' && t.status !== 'CLOSED') return false
      const d = t.updatedAt || t.closedAt || t.resolvedAt
      if (!d) return false
      const then = new Date(d).getTime()
      const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000
      return then >= weekAgo
    }).length
    return { assignedToMe, inProgress, resolvedThisWeek }
  }, [tickets])

  const queuePreview = useMemo(() => {
    return [...tickets]
      .filter((t) => t.status !== 'RESOLVED' && t.status !== 'CLOSED')
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 12)
  }, [tickets])

  const cards = [
    { title: 'Assigned to Me', value: myStats.assignedToMe, icon: Inbox, accent: 'border-blue-200', tint: 'bg-blue-50 text-blue-600' },
    { title: 'In Progress', value: myStats.inProgress, icon: Clock, accent: 'border-yellow-200', tint: 'bg-yellow-50 text-yellow-600' },
    { title: 'Resolved (7 days)', value: myStats.resolvedThisWeek, icon: CheckCircle2, accent: 'border-emerald-200', tint: 'bg-emerald-50 text-emerald-600' },
    { title: 'Avg Resolution (h)', value: '—', icon: Zap, accent: 'border-purple-200', tint: 'bg-purple-50 text-purple-600' },
  ]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Technician Dashboard</h1>
          <p className="text-gray-600 mt-2">
            Hi{user?.firstName ? `, ${user.firstName}` : ''} — tickets assigned to you.
          </p>
        </div>
        <button
          type="button"
          onClick={() => load()}
          disabled={loading}
          className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </header>

      {loadError && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">{loadError}</div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {cards.map((c) => {
          const Icon = c.icon
          return (
            <div
              key={c.title}
              className={`bg-white rounded-lg shadow p-6 border-l-4 ${c.accent} hover:shadow-lg transition-shadow`}
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">{c.title}</p>
                  <p className="text-2xl font-bold text-gray-900 mt-1">
                    {loading && c.title !== 'Avg Resolution (h)' ? '…' : c.value}
                  </p>
                </div>
                <div className={`${c.tint} p-3 rounded-lg`}>
                  <Icon className="h-6 w-6" />
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-lg font-bold text-gray-900">My Queue</h2>
          <Link to="/tickets" className="text-sm text-primary-600 hover:underline">
            View all →
          </Link>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="py-2 pr-4 font-medium">Ticket</th>
                <th className="py-2 pr-4 font-medium">Subject</th>
                <th className="py-2 pr-4 font-medium">Category</th>
                <th className="py-2 pr-4 font-medium">From</th>
                <th className="py-2 pr-4 font-medium">Priority</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 pr-4 font-medium">Updated</th>
                <th className="py-2 font-medium" />
              </tr>
            </thead>
            <tbody>
              {queuePreview.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-6 text-gray-600 text-center">
                    {loading ? 'Loading…' : 'No active assigned tickets.'}
                  </td>
                </tr>
              ) : (
                queuePreview.map((t) => {
                  const fromLabel = t.createdByName
                    ? `${t.createdByName}${t.createdByEmail ? ` · ${t.createdByEmail}` : ''}`
                    : `User #${t.createdBy}`
                  return (
                    <tr key={t.id} className="border-b last:border-0 hover:bg-gray-50">
                      <td className="py-3 pr-4 font-mono text-gray-900">{t.ticketNumber || `#${t.id}`}</td>
                      <td className="py-3 pr-4 text-gray-900 max-w-xs truncate" title={t.title}>
                        {t.title}
                      </td>
                      <td className="py-3 pr-4 text-gray-600">{t.category}</td>
                      <td className="py-3 pr-4 text-gray-700">{fromLabel}</td>
                      <td className="py-3 pr-4">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${priorityClass(t.priority)}`}>
                          {t.priority}
                        </span>
                      </td>
                      <td className="py-3 pr-4 text-gray-600">{t.status.replace(/_/g, ' ')}</td>
                      <td className="py-3 pr-4 text-gray-500 whitespace-nowrap text-xs">
                        {new Date(t.updatedAt).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })}
                      </td>
                      <td className="py-3">
                        <Link to={`/tickets/${t.id}`} className="text-primary-600 hover:underline">
                          Open
                        </Link>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function priorityClass(p: string) {
  switch (p) {
    case 'CRITICAL':
      return 'bg-red-100 text-red-800'
    case 'HIGH':
      return 'bg-orange-100 text-orange-800'
    case 'MEDIUM':
      return 'bg-yellow-100 text-yellow-800'
    default:
      return 'bg-gray-100 text-gray-700'
  }
}

export default TechnicianDashboard
