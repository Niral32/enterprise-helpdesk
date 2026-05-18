import React, { useEffect, useMemo, useState } from 'react'
import { Search, Mail, Phone, X } from 'lucide-react'
import { userAPI } from '../api/users'
import { User } from '../types'
import { useNotification } from '../context/NotificationContext'
import Avatar from '../components/Common/Avatar'

/**
 * Members — a read-only company directory visible to every authenticated role.
 *
 * Why a dedicated page instead of reusing the Users list:
 *   - The existing /users page targets directory-style browsing for staff and
 *     /admin/users gives admins lifecycle controls. Both surface mutating
 *     actions or admin-only affordances.
 *   - Employees and technicians need a way to put a face/name to teammates
 *     without exposing any management UI. This page is just look-and-find.
 */

type RoleFilter = 'ALL' | 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'

const ROLE_BADGE: Record<User['role'], string> = {
  ADMIN: 'bg-purple-100 text-purple-800',
  TECHNICIAN: 'bg-blue-100 text-blue-800',
  EMPLOYEE: 'bg-gray-100 text-gray-800',
}

const STATUS_BADGE: Record<User['status'], string> = {
  ACTIVE: 'bg-emerald-100 text-emerald-800',
  SUSPENDED: 'bg-orange-100 text-orange-800',
  INACTIVE: 'bg-red-100 text-red-800',
  PENDING: 'bg-yellow-100 text-yellow-800',
}

const roleLabel = (r: User['role']) => (r === 'EMPLOYEE' ? 'USER' : r)

const Members: React.FC = () => {
  const { showToast } = useNotification()
  const [members, setMembers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')
  const [openProfile, setOpenProfile] = useState<User | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    userAPI
      .getAll()
      .then((r) => {
        if (cancelled) return
        // Hide soft-deactivated accounts from the team view — they aren't
        // working with you any more. Suspended (temporary block) stays.
        setMembers((r.data || []).filter((u) => u.status !== 'INACTIVE'))
      })
      .catch(() => {
        if (!cancelled) showToast('Could not load members', 'error')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [showToast])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return members
      .filter((u) => {
        if (roleFilter !== 'ALL' && u.role !== roleFilter) return false
        if (!q) return true
        return (
          u.email.toLowerCase().includes(q) ||
          `${u.firstName} ${u.lastName}`.toLowerCase().includes(q) ||
          (u.department || '').toLowerCase().includes(q)
        )
      })
      // Stable visual order: admins first, then technicians, then everyone
      // else, alphabetically within each group. Keeps the page predictable
      // when you scan for a specific person.
      .sort((a, b) => {
        const rank = (r: User['role']) =>
          r === 'ADMIN' ? 0 : r === 'TECHNICIAN' ? 1 : 2
        if (rank(a.role) !== rank(b.role)) return rank(a.role) - rank(b.role)
        return `${a.firstName} ${a.lastName}`.localeCompare(`${b.firstName} ${b.lastName}`)
      })
  }, [members, search, roleFilter])

  const counts = useMemo(() => {
    const c = { ADMIN: 0, TECHNICIAN: 0, EMPLOYEE: 0 } as Record<User['role'], number>
    for (const m of members) c[m.role] = (c[m.role] || 0) + 1
    return c
  }, [members])

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between flex-wrap gap-3">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Members</h1>
          <p className="text-gray-600 mt-1">
            {members.length} {members.length === 1 ? 'person' : 'people'} in your organisation —{' '}
            {counts.ADMIN} admin{counts.ADMIN === 1 ? '' : 's'}, {counts.TECHNICIAN}{' '}
            technician{counts.TECHNICIAN === 1 ? '' : 's'}, {counts.EMPLOYEE}{' '}
            user{counts.EMPLOYEE === 1 ? '' : 's'}.
          </p>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-4 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search by name, email, or department"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          />
        </div>
        <select
          value={roleFilter}
          onChange={(e) => setRoleFilter(e.target.value as RoleFilter)}
          className="px-3 py-2 border border-gray-300 rounded-lg"
        >
          <option value="ALL">All roles</option>
          <option value="ADMIN">Admin</option>
          <option value="TECHNICIAN">Technician</option>
          <option value="EMPLOYEE">User</option>
        </select>
      </div>

      {loading ? (
        <p className="text-gray-600">Loading members…</p>
      ) : filtered.length === 0 ? (
        <div className="bg-white rounded-lg shadow p-8 text-center text-gray-600">
          No members match these filters.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {filtered.map((u) => (
            <button
              key={u.id}
              type="button"
              onClick={() => setOpenProfile(u)}
              className="text-left bg-white rounded-lg shadow p-4 hover:shadow-md transition-shadow border border-transparent hover:border-primary-200 focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
              <div className="flex items-center gap-3">
                <Avatar
                  firstName={u.firstName}
                  lastName={u.lastName}
                  imageUrl={u.profileImageUrl}
                  size={56}
                />
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-gray-900 truncate">
                    {u.firstName} {u.lastName}
                  </p>
                  <p className="text-xs text-gray-600 truncate" title={u.email}>
                    {u.email}
                  </p>
                  <div className="flex items-center gap-1 mt-1">
                    <span
                      className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${ROLE_BADGE[u.role]}`}
                    >
                      {roleLabel(u.role)}
                    </span>
                    {u.status !== 'ACTIVE' && (
                      <span
                        className={`px-2 py-0.5 rounded-full text-[10px] font-medium ${STATUS_BADGE[u.status]}`}
                      >
                        {u.status}
                      </span>
                    )}
                  </div>
                </div>
              </div>
              {u.department && (
                <p className="text-xs text-gray-500 mt-3 truncate">
                  Dept: <span className="text-gray-700">{u.department}</span>
                </p>
              )}
            </button>
          ))}
        </div>
      )}

      {openProfile && <ProfileModal user={openProfile} onClose={() => setOpenProfile(null)} />}
    </div>
  )
}

const ProfileModal: React.FC<{ user: User; onClose: () => void }> = ({ user, onClose }) => (
  <div className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-md overflow-hidden">
      <div className="flex justify-between items-center px-4 py-3 border-b border-gray-100">
        <h2 className="text-lg font-bold text-gray-900">Member profile</h2>
        <button onClick={onClose} className="p-1 rounded hover:bg-gray-100" aria-label="Close">
          <X className="h-4 w-4 text-gray-500" />
        </button>
      </div>
      <div className="p-6 flex flex-col items-center text-center">
        <Avatar
          firstName={user.firstName}
          lastName={user.lastName}
          imageUrl={user.profileImageUrl}
          size={140}
          className="text-3xl mb-4 ring-4 ring-primary-100"
        />
        <h3 className="text-xl font-bold text-gray-900">
          {user.firstName} {user.lastName}
        </h3>
        <div className="flex items-center gap-2 mt-2">
          <span
            className={`px-3 py-1 rounded-full text-xs font-medium ${ROLE_BADGE[user.role]}`}
          >
            {roleLabel(user.role)}
          </span>
          <span
            className={`px-3 py-1 rounded-full text-xs font-medium ${STATUS_BADGE[user.status]}`}
          >
            {user.status}
          </span>
        </div>
        <div className="mt-5 w-full space-y-2 text-sm text-left">
          <a
            href={`mailto:${user.email}`}
            className="flex items-center gap-2 px-3 py-2 rounded-lg border border-gray-200 hover:bg-gray-50 text-gray-700"
          >
            <Mail className="h-4 w-4 text-gray-500" />
            <span className="truncate">{user.email}</span>
          </a>
          {user.phone && (
            <a
              href={`tel:${user.phone}`}
              className="flex items-center gap-2 px-3 py-2 rounded-lg border border-gray-200 hover:bg-gray-50 text-gray-700"
            >
              <Phone className="h-4 w-4 text-gray-500" />
              <span>{user.phone}</span>
            </a>
          )}
          {user.department && (
            <div className="px-3 py-2 rounded-lg border border-gray-200 text-gray-700">
              <span className="text-xs uppercase tracking-wide text-gray-500">Department</span>
              <p className="font-medium">{user.department}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  </div>
)

export default Members
