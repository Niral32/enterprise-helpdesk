import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { Users as UsersIcon, Ticket as TicketIcon, Server, ShieldAlert, AlertCircle, TrendingUp, RefreshCw, type LucideIcon } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { userAPI } from '../api/users'
import { ticketAPI } from '../api/tickets'
import { assetAPI } from '../api/assets'
import { User, Ticket, Asset } from '../types'
import { useNotification } from '../context/NotificationContext'
import { priorityClass, statusClass } from './Tickets'

interface AdminStats {
  totalUsers: number
  totalAdmins: number
  totalTechnicians: number
  totalEmployees: number
  totalTickets: number
  openTickets: number
  inProgressTickets: number
  resolvedTickets: number
  criticalTickets: number
  totalAssets: number
  assetsAvailable: number
  assetsInUse: number
  assetsMaintenance: number
}

const OPEN_LIKE: Ticket['status'][] = [
  'NEW',
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'WAITING_FOR_VENDOR',
  'ON_HOLD',
  'REOPENED',
]

function computeStats(users: User[], tickets: Ticket[], assets: Asset[]): AdminStats {
  const totalAdmins = users.filter((u) => u.role === 'ADMIN').length
  const totalTechnicians = users.filter((u) => u.role === 'TECHNICIAN').length
  const totalEmployees = users.filter((u) => u.role === 'EMPLOYEE').length

  const openTickets = tickets.filter((t) => OPEN_LIKE.includes(t.status)).length
  const inProgressTickets = tickets.filter((t) => t.status === 'IN_PROGRESS').length
  const resolvedTickets = tickets.filter((t) => t.status === 'RESOLVED' || t.status === 'CLOSED').length
  const criticalTickets = tickets.filter(
    (t) =>
      t.priority === 'CRITICAL' &&
      t.status !== 'RESOLVED' &&
      t.status !== 'CLOSED' &&
      t.status !== 'CANCELLED'
  ).length

  const assetsAvailable = assets.filter((a) => a.status === 'AVAILABLE').length
  const assetsInUse = assets.filter((a) => a.status === 'ASSIGNED' || a.status === 'IN_USE').length
  const assetsMaintenance = assets.filter((a) => a.status === 'IN_REPAIR' || a.status === 'MAINTENANCE').length

  return {
    totalUsers: users.length,
    totalAdmins,
    totalTechnicians,
    totalEmployees,
    totalTickets: tickets.length,
    openTickets,
    inProgressTickets,
    resolvedTickets,
    criticalTickets,
    totalAssets: assets.length,
    assetsAvailable,
    assetsInUse,
    assetsMaintenance,
  }
}

type AdminKpiCard = {
  title: string
  value: number
  icon: LucideIcon
  accent: string
  tint: string
  href?: string
  onFilter?: () => void
}

const emptyStats: AdminStats = {
  totalUsers: 0,
  totalAdmins: 0,
  totalTechnicians: 0,
  totalEmployees: 0,
  totalTickets: 0,
  openTickets: 0,
  inProgressTickets: 0,
  resolvedTickets: 0,
  criticalTickets: 0,
  totalAssets: 0,
  assetsAvailable: 0,
  assetsInUse: 0,
  assetsMaintenance: 0,
}

/**
 * Admin Dashboard — live KPIs from user, ticket, and asset APIs (ADMIN role required).
 */
const AdminDashboard: React.FC = () => {
  const { user } = useAuth()
  const { showToast } = useNotification()
  const location = useLocation()
  const [loading, setLoading] = useState<boolean>(true)
  const [stats, setStats] = useState<AdminStats>(emptyStats)
  const [allTickets, setAllTickets] = useState<Ticket[]>([])
  /** When set, recent-tickets table is filtered to match the KPI the admin clicked. */
  const [ticketListScope, setTicketListScope] = useState<'none' | 'open' | 'critical' | 'in_progress' | 'resolved'>(
    'none'
  )
  const [loadError, setLoadError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      const [usersRes, ticketsRes, assetsRes] = await Promise.all([
        userAPI.getAll(),
        ticketAPI.getAllForAdmin(),
        assetAPI.getAll(),
      ])
      const ticketList = ticketsRes.data ?? []
      setAllTickets(ticketList)
      setStats(computeStats(usersRes.data ?? [], ticketList, assetsRes.data ?? []))
    } catch {
      setLoadError('Could not load dashboard metrics. Check that services are running and you are signed in as admin.')
      setStats(emptyStats)
      setAllTickets([])
      showToast('Could not load admin dashboard data', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    load()
  }, [load, location.key])

  const cards = useMemo<AdminKpiCard[]>(
    () => [
      {
        title: 'Total Users',
        value: stats.totalUsers,
        icon: UsersIcon,
        accent: 'border-blue-200',
        tint: 'bg-blue-50 text-blue-600',
        href: '/users',
      },
      {
        title: 'Open Tickets',
        value: stats.openTickets,
        icon: TicketIcon,
        accent: 'border-orange-200',
        tint: 'bg-orange-50 text-orange-600',
        onFilter: () => setTicketListScope('open'),
      },
      {
        title: 'Critical Tickets',
        value: stats.criticalTickets,
        icon: ShieldAlert,
        accent: 'border-red-200',
        tint: 'bg-red-50 text-red-600',
        onFilter: () => setTicketListScope('critical'),
      },
      {
        title: 'Active Assets',
        value: stats.assetsInUse,
        icon: Server,
        accent: 'border-emerald-200',
        tint: 'bg-emerald-50 text-emerald-600',
        href: '/assets',
      },
    ],
    [stats, setTicketListScope]
  )

  const recentTicketsFiltered = useMemo(() => {
    let list = [...allTickets]
    switch (ticketListScope) {
      case 'open':
        list = list.filter((t) => OPEN_LIKE.includes(t.status))
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
      case 'in_progress':
        list = list.filter((t) => t.status === 'IN_PROGRESS')
        break
      case 'resolved':
        list = list.filter((t) => t.status === 'RESOLVED' || t.status === 'CLOSED')
        break
      default:
        break
    }
    return list
      .sort((a, b) => new Date(b.updatedAt || b.createdAt).getTime() - new Date(a.updatedAt || a.createdAt).getTime())
      .slice(0, 15)
  }, [allTickets, ticketListScope])

  const scopeLabel =
    ticketListScope === 'none'
      ? null
      : {
          open: 'Open tickets',
          critical: 'Critical (unresolved)',
          in_progress: 'In progress',
          resolved: 'Resolved / closed',
        }[ticketListScope]

  return (
    <div className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Admin Dashboard</h1>
          <p className="text-gray-600 mt-2">
            Welcome{user?.firstName ? `, ${user.firstName}` : ''}. System-wide overview of users, tickets, and assets.
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
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          {loadError}
        </div>
      )}

      {/* Top-line KPIs — links drill into other admin areas; ticket cards filter the recent table below */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {cards.map((c) => {
          const Icon = c.icon
          const body = (
            <>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-gray-600">{c.title}</p>
                  <p className="text-2xl font-bold text-gray-900 mt-1">
                    {loading ? '…' : c.value.toLocaleString()}
                  </p>
                </div>
                <div className={`${c.tint} p-3 rounded-lg`}>
                  <Icon className="h-6 w-6" />
                </div>
              </div>
              {c.href ? (
                <p className="text-xs text-primary-600 mt-2">Click to open →</p>
              ) : c.onFilter ? (
                <p className="text-xs text-gray-500 mt-2">Click to filter recent tickets</p>
              ) : null}
            </>
          )
          const shellClass = `bg-white rounded-lg shadow p-6 border-l-4 ${c.accent} hover:shadow-lg transition-shadow`

          if (c.href) {
            return (
              <Link key={c.title} to={c.href} className={`${shellClass} block no-underline text-inherit`}>
                {body}
              </Link>
            )
          }

          if (c.onFilter) {
            return (
              <button
                key={c.title}
                type="button"
                onClick={c.onFilter}
                className={`${shellClass} w-full text-left cursor-pointer`}
              >
                {body}
              </button>
            )
          }

          return (
            <div key={c.title} className={shellClass}>
              {body}
            </div>
          )
        })}
      </div>

      {/* Breakdown panels */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <BreakdownCard
          title="Users by Role"
          rows={[
            { label: 'Admins', value: stats.totalAdmins },
            { label: 'Technicians', value: stats.totalTechnicians },
            { label: 'Employees', value: stats.totalEmployees },
          ]}
        />
        <AdminTicketStatusBreakdown
          stats={stats}
          onPickScope={(s) => setTicketListScope(s)}
        />
        <BreakdownCard
          title="Assets by Status"
          rows={[
            { label: 'Available', value: stats.assetsAvailable },
            { label: 'In Use', value: stats.assetsInUse },
            { label: 'Maintenance', value: stats.assetsMaintenance },
          ]}
        />
      </div>

      {/* Recent tickets — respects KPI / breakdown filters */}
      <div className="bg-white rounded-lg shadow p-6">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <h2 className="text-lg font-bold text-gray-900">Recent tickets</h2>
          <div className="flex flex-wrap items-center gap-2">
            {scopeLabel && (
              <span className="inline-flex items-center gap-2 px-3 py-1 rounded-full text-xs font-medium bg-primary-50 text-primary-800 border border-primary-100">
                {scopeLabel}
                <button
                  type="button"
                  onClick={() => setTicketListScope('none')}
                  className="text-primary-600 hover:underline"
                >
                  Clear
                </button>
              </span>
            )}
            <Link to="/tickets" className="text-sm text-primary-600 hover:underline">
              View all →
            </Link>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="py-2 pr-4 font-medium">Ticket</th>
                <th className="py-2 pr-4 font-medium">Title</th>
                <th className="py-2 pr-4 font-medium">Requester</th>
                <th className="py-2 pr-4 font-medium">Priority</th>
                <th className="py-2 pr-4 font-medium">Status</th>
                <th className="py-2 pr-4 font-medium">Updated</th>
                <th className="py-2 font-medium" />
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-gray-500">
                    Loading…
                  </td>
                </tr>
              ) : recentTicketsFiltered.length === 0 ? (
                <tr>
                  <td colSpan={7} className="py-8 text-center text-gray-600">
                    {allTickets.length === 0
                      ? 'No tickets in the system yet.'
                      : 'No tickets in this view. Try clearing the filter or pick another KPI.'}
                  </td>
                </tr>
              ) : (
                recentTicketsFiltered.map((t) => (
                  <tr key={t.id} className="border-b last:border-0 hover:bg-gray-50">
                    <td className="py-3 pr-4 font-mono text-gray-900">{t.ticketNumber || `#${t.id}`}</td>
                    <td className="py-3 pr-4 text-gray-900 max-w-xs truncate">{t.title}</td>
                    <td className="py-3 pr-4 text-gray-700">
                      {t.createdByName || `User #${t.createdBy}`}
                    </td>
                    <td className="py-3 pr-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${priorityClass(t.priority)}`}>
                        {t.priority}
                      </span>
                    </td>
                    <td className="py-3 pr-4">
                      <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusClass(t.status)}`}>
                        {t.status.replace(/_/g, ' ')}
                      </span>
                    </td>
                    <td className="py-3 pr-4 text-gray-600 whitespace-nowrap">{fmtRel(t.updatedAt)}</td>
                    <td className="py-3">
                      <Link to={`/tickets/${t.id}`} className="text-primary-600 hover:underline">
                        Open
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Critical alerts + quick actions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-4 flex items-center">
            <AlertCircle className="h-5 w-5 text-red-500 mr-2" />
            Needs Attention
          </h2>
          <ul className="space-y-3 text-sm">
            <li className="flex justify-between p-3 bg-red-50 rounded-lg">
              <span className="text-red-800 font-medium">{stats.criticalTickets} critical tickets unresolved</span>
              <Link to="/tickets?priority=CRITICAL" className="text-red-600 hover:underline">View</Link>
            </li>
            <li className="flex justify-between p-3 bg-orange-50 rounded-lg">
              <span className="text-orange-800 font-medium">{stats.assetsMaintenance} assets in maintenance</span>
              <Link to="/assets?status=MAINTENANCE" className="text-orange-600 hover:underline">View</Link>
            </li>
            <li className="flex justify-between p-3 bg-blue-50 rounded-lg">
              <span className="text-blue-800 font-medium">{stats.openTickets} open tickets pending assignment</span>
              <Link to="/tickets?status=OPEN" className="text-blue-600 hover:underline">View</Link>
            </li>
          </ul>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-4 flex items-center">
            <TrendingUp className="h-5 w-5 text-emerald-500 mr-2" />
            Quick Actions
          </h2>
          <div className="space-y-3">
            <Link to="/users" className="block w-full text-center px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition-colors">
              Manage Users
            </Link>
            <Link to="/assets" className="block w-full text-center px-4 py-2 bg-primary-100 text-primary-700 rounded-lg hover:bg-primary-200 transition-colors">
              Manage Assets
            </Link>
            <Link to="/tickets" className="block w-full text-center px-4 py-2 bg-primary-100 text-primary-700 rounded-lg hover:bg-primary-200 transition-colors">
              View All Tickets
            </Link>
          </div>
        </div>
      </div>
    </div>
  )
}

type TicketTableScope = 'none' | 'open' | 'critical' | 'in_progress' | 'resolved'

const AdminTicketStatusBreakdown: React.FC<{
  stats: AdminStats
  onPickScope: (s: Exclude<TicketTableScope, 'none' | 'critical'>) => void
}> = ({ stats, onPickScope }) => {
  const rows: { label: string; value: number; scope: 'open' | 'in_progress' | 'resolved' }[] = [
    { label: 'Open', value: stats.openTickets, scope: 'open' },
    { label: 'In Progress', value: stats.inProgressTickets, scope: 'in_progress' },
    { label: 'Resolved / Closed', value: stats.resolvedTickets, scope: 'resolved' },
  ]
  const total = rows.reduce((s, r) => s + r.value, 0) || 1
  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-bold text-gray-900 mb-4">Tickets by Status</h3>
      <p className="text-xs text-gray-500 mb-3">Click a row to filter recent tickets.</p>
      <div className="space-y-3">
        {rows.map((r) => (
          <button
            type="button"
            key={r.label}
            onClick={() => onPickScope(r.scope)}
            className="w-full text-left rounded-lg hover:bg-gray-50 transition-colors p-1 -m-1"
          >
            <div className="flex justify-between text-sm mb-1">
              <span className="text-gray-700">{r.label}</span>
              <span className="font-semibold text-gray-900">{r.value}</span>
            </div>
            <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
              <div
                className="h-full bg-primary-600"
                style={{ width: `${Math.min(100, Math.round((r.value / total) * 100))}%` }}
              />
            </div>
          </button>
        ))}
      </div>
    </div>
  )
}

function fmtRel(s?: string) {
  if (!s) return '—'
  try {
    return new Date(s).toLocaleString(undefined, { dateStyle: 'short', timeStyle: 'short' })
  } catch {
    return s
  }
}

interface BreakdownCardProps {
  title: string
  rows: { label: string; value: number }[]
}

const BreakdownCard: React.FC<BreakdownCardProps> = ({ title, rows }) => {
  const total = rows.reduce((s, r) => s + r.value, 0) || 1
  return (
    <div className="bg-white rounded-lg shadow p-6">
      <h3 className="text-lg font-bold text-gray-900 mb-4">{title}</h3>
      <div className="space-y-3">
        {rows.map((r) => (
          <div key={r.label}>
            <div className="flex justify-between text-sm mb-1">
              <span className="text-gray-700">{r.label}</span>
              <span className="font-semibold text-gray-900">{r.value}</span>
            </div>
            <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
              <div
                className="h-full bg-primary-600"
                style={{ width: `${Math.min(100, Math.round((r.value / total) * 100))}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default AdminDashboard
