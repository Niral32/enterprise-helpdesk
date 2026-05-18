import React, { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, Search } from 'lucide-react'
import { ticketAPI } from '../api/tickets'
import { Ticket, TicketStatus } from '../types'
import { useNotification } from '../context/NotificationContext'
import { useAuth } from '../context/AuthContext'

/**
 * Tickets list — the same page works for all roles, but the data source is
 * role-aware:
 *
 *   ADMIN      → /tickets/admin/all       (every ticket)
 *   TECHNICIAN → /tickets/technician/queue (only tickets assigned to me)
 *   EMPLOYEE   → /tickets/my              (only tickets I created)
 *
 * That keeps a single page in the route table while satisfying the HESK rule
 * that USER cannot see other users' tickets.
 */
const Tickets: React.FC = () => {
  const { user } = useAuth()
  const { showToast } = useNotification()

  const [searchQuery, setSearchQuery] = useState('')
  const [filterStatus, setFilterStatus] = useState<TicketStatus | 'ALL'>('ALL')
  const [tickets, setTickets] = useState<Ticket[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const loader =
      user?.role === 'ADMIN'
        ? ticketAPI.getAllForAdmin
        : user?.role === 'TECHNICIAN'
        ? ticketAPI.getMyQueue
        : ticketAPI.getMine
    loader()
      .then((r) => {
        if (!cancelled) setTickets(r.data)
      })
      .catch(() => {
        if (!cancelled) showToast('Could not load tickets', 'error')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [user?.role, showToast])

  const filtered = useMemo(() => {
    return tickets
      .filter((t) => {
        if (filterStatus !== 'ALL' && t.status !== filterStatus) return false
        const q = searchQuery.trim().toLowerCase()
        if (!q) return true
        return (
          t.title.toLowerCase().includes(q) ||
          (t.description || '').toLowerCase().includes(q) ||
          (t.ticketNumber || '').toLowerCase().includes(q) ||
          (t.createdByName || '').toLowerCase().includes(q) ||
          (t.createdByEmail || '').toLowerCase().includes(q) ||
          (t.assignedToName || '').toLowerCase().includes(q) ||
          (t.assignedToEmail || '').toLowerCase().includes(q)
        )
      })
      // Newest first — id is monotonically increasing and the only field
      // guaranteed present even before createdAt enrichment.
      .sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
  }, [tickets, filterStatus, searchQuery])

  const heading =
    user?.role === 'ADMIN'
      ? 'All Tickets'
      : user?.role === 'TECHNICIAN'
      ? 'My Queue'
      : 'My Tickets'

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">{heading}</h1>
        <Link
          to="/tickets/create"
          className="flex items-center space-x-2 bg-primary-600 hover:bg-primary-700 text-white px-4 py-2 rounded-lg transition-colors"
        >
          <Plus className="h-5 w-5" />
          <span>Create Ticket</span>
        </Link>
      </div>

      <div className="bg-white rounded-lg shadow p-4 flex flex-col md:flex-row gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
          <input
            type="text"
            placeholder="Search title, #, creator name, email, assignee…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value as TicketStatus | 'ALL')}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
        >
          <option value="ALL">All Status</option>
          <option value="NEW">New</option>
          <option value="OPEN">Open</option>
          <option value="ASSIGNED">Assigned</option>
          <option value="IN_PROGRESS">In Progress</option>
          <option value="WAITING_FOR_USER">Waiting for User</option>
          <option value="WAITING_FOR_VENDOR">Waiting for Vendor</option>
          <option value="ON_HOLD">On Hold</option>
          <option value="RESOLVED">Resolved</option>
          <option value="CLOSED">Closed</option>
          <option value="REOPENED">Reopened</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-600">Loading tickets…</div>
        ) : filtered.length === 0 ? (
          <div className="p-8 text-center text-gray-600">
            {tickets.length === 0
              ? 'No tickets yet — create one to get started.'
              : 'No tickets match these filters.'}
          </div>
        ) : (
          <div className="overflow-x-auto">
          <table className="w-full min-w-[960px]">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900 whitespace-nowrap">Ticket No</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Created By</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Email</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Title</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Priority</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Status</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900">Assigned To</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900 whitespace-nowrap">Created</th>
                <th className="px-4 py-3 text-left text-sm font-semibold text-gray-900"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {filtered.map((t) => (
                <tr key={t.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-4 text-sm font-mono text-gray-900 whitespace-nowrap">
                    {t.ticketNumber || `#${t.id}`}
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-900 max-w-[140px]">
                    <span className="line-clamp-2" title={t.createdByName || `User #${t.createdBy}`}>
                      {t.createdByName || `User #${t.createdBy}`}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-700 max-w-[180px] truncate" title={t.createdByEmail || ''}>
                    {t.createdByEmail ? (
                      <a href={`mailto:${t.createdByEmail}`} className="text-primary-600 hover:underline">
                        {t.createdByEmail}
                      </a>
                    ) : (
                      '—'
                    )}
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-900 max-w-xs truncate" title={t.title}>
                    {t.title}
                  </td>
                  <td className="px-4 py-4 text-sm">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-medium ${priorityClass(
                        t.priority
                      )}`}
                    >
                      {t.priority}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-sm">
                    <span
                      className={`px-3 py-1 rounded-full text-xs font-medium ${statusClass(
                        t.status
                      )}`}
                    >
                      {t.status.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-800 max-w-[140px]">
                    {t.assignedTo == null ? (
                      'Unassigned'
                    ) : (
                      <span className="line-clamp-2" title={[t.assignedToName, t.assignedToEmail].filter(Boolean).join(' — ')}>
                        {t.assignedToName || `User #${t.assignedTo}`}
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-4 text-sm text-gray-600 whitespace-nowrap">{fmt(t.createdAt)}</td>
                  <td className="px-4 py-4 text-sm whitespace-nowrap">
                    <Link
                      to={`/tickets/${t.id}`}
                      className="text-primary-600 hover:text-primary-700 font-medium"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>
        )}
      </div>
    </div>
  )
}

// Shared color helpers — reused by TicketDetails too. Keep here for now;
// when more pages need them, lift into a common file.
export function priorityClass(priority: string) {
  switch (priority) {
    case 'CRITICAL':
      return 'bg-red-100 text-red-800'
    case 'HIGH':
      return 'bg-orange-100 text-orange-800'
    case 'MEDIUM':
      return 'bg-yellow-100 text-yellow-800'
    case 'LOW':
      return 'bg-green-100 text-green-800'
    default:
      return 'bg-gray-100 text-gray-800'
  }
}

export function statusClass(status: string) {
  switch (status) {
    case 'NEW':
      return 'bg-sky-100 text-sky-800'
    case 'OPEN':
      return 'bg-blue-100 text-blue-800'
    case 'ASSIGNED':
      return 'bg-indigo-100 text-indigo-800'
    case 'IN_PROGRESS':
      return 'bg-purple-100 text-purple-800'
    case 'WAITING_FOR_USER':
      return 'bg-amber-100 text-amber-800'
    case 'WAITING_FOR_VENDOR':
      return 'bg-orange-100 text-orange-800'
    case 'ON_HOLD':
      return 'bg-gray-200 text-gray-700'
    case 'RESOLVED':
      return 'bg-emerald-100 text-emerald-800'
    case 'CLOSED':
      return 'bg-gray-100 text-gray-700'
    case 'REOPENED':
      return 'bg-rose-100 text-rose-800'
    case 'CANCELLED':
      return 'bg-stone-200 text-stone-700'
    default:
      return 'bg-gray-100 text-gray-800'
  }
}

function fmt(s?: string) {
  if (!s) return '—'
  try {
    return new Date(s).toLocaleDateString()
  } catch {
    return s
  }
}

export default Tickets
