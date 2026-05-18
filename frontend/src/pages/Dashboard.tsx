import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Ticket, TrendingUp, AlertCircle, RefreshCw } from 'lucide-react'
import { ticketAPI } from '../api/tickets'
import { Ticket as TicketModel, TicketStatus } from '../types'
import { useAuth } from '../context/AuthContext'
import { useNotification } from '../context/NotificationContext'
import { statusClass } from './Tickets'

const OPEN_LIKE: TicketStatus[] = [
  'NEW',
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'WAITING_FOR_VENDOR',
  'ON_HOLD',
  'REOPENED',
]

type DashScope = 'none' | 'all' | 'open' | 'resolved' | 'critical'

const Dashboard: React.FC = () => {
  const { user } = useAuth()
  const { showToast } = useNotification()
  const location = useLocation()
  const [tickets, setTickets] = useState<TicketModel[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [scope, setScope] = useState<DashScope>('none')

  const loadTickets = useCallback(async () => {
    if (!user?.role) return
    setLoading(true)
    setLoadError(null)
    try {
      const loader =
        user.role === 'ADMIN'
          ? ticketAPI.getAllForAdmin
          : user.role === 'TECHNICIAN'
          ? ticketAPI.getMyQueue
          : ticketAPI.getMine
      const { data } = await loader()
      setTickets(data ?? [])
    } catch {
      setLoadError('Could not load tickets. Check that the API gateway is running and you are signed in.')
      setTickets([])
      showToast('Could not load dashboard data', 'error')
    } finally {
      setLoading(false)
    }
  }, [user?.role, showToast])

  useEffect(() => {
    loadTickets()
  }, [loadTickets, location.key])

  const stats = useMemo(() => {
    const total = tickets.length
    const openTickets = tickets.filter((t) => OPEN_LIKE.includes(t.status)).length
    const resolvedTickets = tickets.filter((t) => t.status === 'RESOLVED' || t.status === 'CLOSED').length
    const criticalTickets = tickets.filter(
      (t) =>
        t.priority === 'CRITICAL' &&
        t.status !== 'RESOLVED' &&
        t.status !== 'CLOSED' &&
        t.status !== 'CANCELLED'
    ).length
    return { totalTickets: total, openTickets, resolvedTickets, criticalTickets }
  }, [tickets])

  const recentFiltered = useMemo(() => {
    let list = [...tickets]
    switch (scope) {
      case 'all':
        break
      case 'open':
        list = list.filter((t) => OPEN_LIKE.includes(t.status))
        break
      case 'resolved':
        list = list.filter((t) => t.status === 'RESOLVED' || t.status === 'CLOSED')
        break
      case 'critical':
        list = list.filter(
          (t) =>
            t.priority === 'CRITICAL' &&
            t.status !== 'RESOLVED' &&
            t.status !== 'CLOSED' &&
            t.status !== 'CANCELLED'
        )
        break
      case 'none':
      default:
        list = list
          .sort((a, b) => new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime())
          .slice(0, 5)
        return list
    }
    return list
      .sort((a, b) => new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime())
      .slice(0, 15)
  }, [tickets, scope])

  const statCards: {
    title: string
    value: number
    icon: typeof Ticket
    color: string
    borderColor: string
    targetScope: Exclude<DashScope, 'none'>
  }[] = [
    {
      title: 'Total Tickets',
      value: stats.totalTickets,
      icon: Ticket,
      color: 'bg-blue-50 text-blue-600',
      borderColor: 'border-blue-200',
      targetScope: 'all',
    },
    {
      title: 'Open / In Progress',
      value: stats.openTickets,
      icon: AlertCircle,
      color: 'bg-orange-50 text-orange-600',
      borderColor: 'border-orange-200',
      targetScope: 'open',
    },
    {
      title: 'Resolved / Closed',
      value: stats.resolvedTickets,
      icon: TrendingUp,
      color: 'bg-green-50 text-green-600',
      borderColor: 'border-green-200',
      targetScope: 'resolved',
    },
    {
      title: 'Critical',
      value: stats.criticalTickets,
      icon: AlertCircle,
      color: 'bg-red-50 text-red-600',
      borderColor: 'border-red-200',
      targetScope: 'critical',
    },
  ]

  const scopeDescription =
    scope === 'none'
      ? 'Latest activity'
      : scope === 'all'
      ? 'All tickets'
      : scope === 'open'
      ? 'Open / in progress'
      : scope === 'resolved'
      ? 'Resolved / closed'
      : 'Critical (unresolved)'

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-600 mt-2">
            {user?.role === 'EMPLOYEE'
              ? 'Overview of your support tickets.'
              : user?.role === 'TECHNICIAN'
              ? 'Tickets assigned to you.'
              : 'Organization-wide ticket overview.'}
          </p>
        </div>
        <button
          type="button"
          onClick={() => loadTickets()}
          disabled={loading}
          className="inline-flex items-center gap-2 px-3 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {loadError && (
        <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-900">{loadError}</div>
      )}

      {loading ? (
        <p className="text-gray-600">Loading statistics…</p>
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {statCards.map((card) => {
              const Icon = card.icon
              const selected = scope !== 'none' && scope === card.targetScope
              return (
                <button
                  key={card.title}
                  type="button"
                  onClick={() => setScope(card.targetScope)}
                  className={`text-left bg-white rounded-lg shadow p-6 border-l-4 ${card.borderColor} hover:shadow-lg transition-shadow ${
                    selected ? 'ring-2 ring-primary-500 ring-offset-2' : ''
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm font-medium text-gray-600">{card.title}</p>
                      <p className="text-2xl font-bold text-gray-900 mt-1">{card.value}</p>
                    </div>
                    <div className={`${card.color} p-3 rounded-lg`}>
                      <Icon className="h-6 w-6" />
                    </div>
                  </div>
                  <p className="text-xs text-gray-500 mt-2">Click to filter the list below</p>
                </button>
              )
            })}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <div className="bg-white rounded-lg shadow p-6">
              <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
                <h2 className="text-lg font-bold text-gray-900">Recent Tickets</h2>
                <div className="flex flex-wrap items-center gap-2">
                  {scope !== 'none' && (
                    <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium bg-primary-50 text-primary-800 border border-primary-100">
                      {scopeDescription}
                      <button
                        type="button"
                        onClick={() => setScope('none')}
                        className="text-primary-600 hover:underline"
                      >
                        Show latest
                      </button>
                    </span>
                  )}
                  <Link to="/tickets" className="text-sm text-primary-600 hover:underline">
                    View all →
                  </Link>
                </div>
              </div>
              {recentFiltered.length === 0 ? (
                <p className="text-gray-600 text-sm">No tickets in this view.</p>
              ) : (
                <div className="space-y-4">
                  {recentFiltered.map((t) => (
                    <Link
                      key={t.id}
                      to={`/tickets/${t.id}`}
                      className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100"
                    >
                      <div className="min-w-0">
                        <p className="font-medium text-gray-900">
                          {t.ticketNumber || `Ticket #${t.id}`}
                        </p>
                        <p className="text-sm text-gray-600 truncate">{t.title}</p>
                        <p className="text-xs text-gray-500 mt-1">
                          Updated {new Date(t.updatedAt).toLocaleString()}
                        </p>
                      </div>
                      <span
                        className={`px-3 py-1 rounded-full text-sm font-medium whitespace-nowrap shrink-0 ml-2 ${statusClass(
                          t.status
                        )}`}
                      >
                        {t.status.replace(/_/g, ' ')}
                      </span>
                    </Link>
                  ))}
                </div>
              )}
            </div>

            <div className="bg-white rounded-lg shadow p-6">
              <h2 className="text-lg font-bold text-gray-900 mb-4">Quick Actions</h2>
              <div className="space-y-3">
                <Link
                  to="/tickets/create"
                  className="block w-full text-center px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors"
                >
                  Create New Ticket
                </Link>
                <Link
                  to="/tickets"
                  className="block w-full text-center px-4 py-2 bg-primary-100 text-primary-600 rounded-lg hover:bg-primary-200 transition-colors"
                >
                  View All Tickets
                </Link>
                <Link
                  to="/profile"
                  className="block w-full text-center px-4 py-2 bg-primary-100 text-primary-600 rounded-lg hover:bg-primary-200 transition-colors"
                >
                  My Profile
                </Link>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  )
}

export default Dashboard
