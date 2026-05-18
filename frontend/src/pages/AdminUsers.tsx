import React, { useEffect, useMemo, useState } from 'react'
import {
  CheckCircle2,
  Plus,
  RefreshCw,
  Search,
  ShieldOff,
  ShieldCheck,
  KeyRound,
  LogIn,
  Ban,
  X,
  UserCog,
} from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useNotification } from '../context/NotificationContext'
import { userAPI, CreateUserRequest, UpdateUserRequest } from '../api/users'
import { authAPI } from '../api/auth'
import { User } from '../types'

/**
 * Admin Users page — full CRUD + lifecycle actions for users and technicians.
 *
 * Wires up the new endpoints added in Priority 2.1:
 *   POST   /api/users/admin
 *   PATCH  /api/users/{id}/block | unblock | deactivate | role
 *   PATCH  /api/auth/admin/reset-password/{id}
 *
 * The page is rendered only for ADMIN role (route is protected in App.tsx).
 * UI rules:
 *   - "USER" label in the UI maps to the EMPLOYEE role internally.
 *   - Destructive actions (block / deactivate / role change / password reset)
 *     prompt with a confirmation modal.
 */

type RoleFilter = 'ALL' | 'ADMIN' | 'TECHNICIAN' | 'EMPLOYEE'
type StatusFilter = 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'INACTIVE' | 'PENDING'

interface ConfirmState {
  title: string
  message: string
  onConfirm: () => Promise<void> | void
}

const AdminUsers: React.FC = () => {
  const { user: me } = useAuth()
  const { showToast } = useNotification()

  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<RoleFilter>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')

  const [createOpen, setCreateOpen] = useState(false)
  const [enableLoginOpen, setEnableLoginOpen] = useState(false)
  /** When set, Enable sign-in modal pre-fills this email (from the row action) to avoid typos. */
  const [enableLoginPrefill, setEnableLoginPrefill] = useState<string | undefined>(undefined)
  const [resetTarget, setResetTarget] = useState<User | null>(null)
  const [editTarget, setEditTarget] = useState<User | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)

  const reload = async () => {
    setLoading(true)
    try {
      const r = await userAPI.getAll()
      setUsers(r.data)
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Failed to load users', 'error')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    reload()
  }, [])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return users
      .filter((u) => {
        if (roleFilter !== 'ALL' && u.role !== roleFilter) return false
        if (statusFilter !== 'ALL' && u.status !== statusFilter) return false
        if (!q) return true
        return (
          u.email.toLowerCase().includes(q) ||
          `${u.firstName} ${u.lastName}`.toLowerCase().includes(q) ||
          (u.department || '').toLowerCase().includes(q)
        )
      })
      .sort((a, b) => b.id - a.id) // newest user first
  }, [users, search, roleFilter, statusFilter])

  const runAction = async (label: string, fn: () => Promise<unknown>) => {
    try {
      await fn()
      showToast(`${label} succeeded`, 'success')
      reload()
    } catch (e: any) {
      showToast(e.response?.data?.message || `${label} failed`, 'error')
    }
  }

  return (
    <div className="space-y-6">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">User Management</h1>
          <p className="text-gray-600 mt-1">
            Create technicians, manage account status, and reset passwords.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={reload}
            className="flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
          <button
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
          >
            <Plus className="h-4 w-4" /> New User
          </button>
          <button
            type="button"
            onClick={() => {
              setEnableLoginPrefill(undefined)
              setEnableLoginOpen(true)
            }}
            className="flex items-center gap-2 px-4 py-2 border border-primary-600 text-primary-700 rounded-lg hover:bg-primary-50"
            title="For users who appear in this list but cannot sign in (directory-only accounts)"
          >
            <KeyRound className="h-4 w-4" /> Enable sign-in
          </button>
        </div>
      </header>

      {/* Filters */}
      <div className="bg-white rounded-lg shadow p-4 flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
          <input
            type="text"
            placeholder="Search name, email, department"
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
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}
          className="px-3 py-2 border border-gray-300 rounded-lg"
        >
          <option value="ALL">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="INACTIVE">Inactive</option>
          <option value="PENDING">Pending</option>
        </select>
      </div>

      {/* Table */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50 text-gray-600">
              <tr>
                <th className="text-left py-3 px-4 font-medium">Name</th>
                <th className="text-left py-3 px-4 font-medium">Email</th>
                <th className="text-left py-3 px-4 font-medium">Role</th>
                <th className="text-left py-3 px-4 font-medium">Status</th>
                <th className="text-left py-3 px-4 font-medium">Department</th>
                <th className="text-right py-3 px-4 font-medium">Actions</th>
              </tr>
            </thead>
            <tbody>
              {loading && (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-gray-500">
                    Loading…
                  </td>
                </tr>
              )}
              {!loading && filtered.length === 0 && (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-gray-500">
                    No users match these filters.
                  </td>
                </tr>
              )}
              {!loading &&
                filtered.map((u) => {
                  const isMe = me?.id === u.id
                  return (
                    <tr key={u.id} className="border-t hover:bg-gray-50">
                      <td className="py-3 px-4 text-gray-900">
                        {u.firstName} {u.lastName}
                        {isMe && <span className="ml-2 text-xs text-primary-600">(you)</span>}
                      </td>
                      <td className="py-3 px-4 text-gray-700">{u.email}</td>
                      <td className="py-3 px-4">
                        <RoleBadge role={u.role} />
                      </td>
                      <td className="py-3 px-4">
                        <StatusBadge status={u.status} />
                      </td>
                      <td className="py-3 px-4 text-gray-700">{u.department || '—'}</td>
                      <td className="py-3 px-4">
                        <div className="flex justify-end gap-1">
                          {u.status !== 'SUSPENDED' && (
                            <IconButton
                              title="Block (suspend)"
                              onClick={() =>
                                setConfirm({
                                  title: 'Block user',
                                  message: `Block ${u.email}? They will be unable to log in until unblocked.`,
                                  onConfirm: () => runAction('Block', () => userAPI.block(u.id)),
                                })
                              }
                              disabled={isMe}
                            >
                              <Ban className="h-4 w-4 text-orange-600" />
                            </IconButton>
                          )}
                          {u.status === 'SUSPENDED' && (
                            <IconButton
                              title="Unblock"
                              onClick={() =>
                                runAction('Unblock', () => userAPI.unblock(u.id))
                              }
                            >
                              <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                            </IconButton>
                          )}
                          {u.status !== 'INACTIVE' && (
                            <IconButton
                              title="Deactivate"
                              onClick={() =>
                                setConfirm({
                                  title: 'Deactivate user',
                                  message: `Deactivate ${u.email}? Existing tickets remain but they cannot log in.`,
                                  onConfirm: () =>
                                    runAction('Deactivate', () => userAPI.deactivate(u.id)),
                                })
                              }
                              disabled={isMe}
                            >
                              <ShieldOff className="h-4 w-4 text-red-600" />
                            </IconButton>
                          )}
                          {(u.role === 'EMPLOYEE' || u.role === 'TECHNICIAN') && (
                            <IconButton
                              title="Enable sign-in (use if they have no auth account yet)"
                              onClick={() => {
                                setEnableLoginPrefill(u.email)
                                setEnableLoginOpen(true)
                              }}
                            >
                              <LogIn className="h-4 w-4 text-violet-600" />
                            </IconButton>
                          )}
                          <IconButton
                            title="Reset password"
                            onClick={() => setResetTarget(u)}
                          >
                            <KeyRound className="h-4 w-4 text-blue-600" />
                          </IconButton>
                          <IconButton
                            title="Edit profile (name, email, role, dept, phone)"
                            onClick={() => setEditTarget(u)}
                          >
                            <UserCog className="h-4 w-4 text-indigo-600" />
                          </IconButton>
                        </div>
                      </td>
                    </tr>
                  )
                })}
            </tbody>
          </table>
        </div>
      </div>

      {/* Create modal */}
      {createOpen && (
        <CreateUserModal
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false)
            reload()
            showToast('User created', 'success')
          }}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}

      {enableLoginOpen && (
        <EnableSignInModal
          initialEmail={enableLoginPrefill}
          onClose={() => {
            setEnableLoginOpen(false)
            setEnableLoginPrefill(undefined)
          }}
          onDone={(success, msg) => {
            setEnableLoginOpen(false)
            setEnableLoginPrefill(undefined)
            showToast(msg, success ? 'success' : 'error')
            if (success) reload()
          }}
        />
      )}

      {/* Reset password modal */}
      {resetTarget && (
        <ResetPasswordModal
          target={resetTarget}
          onClose={() => setResetTarget(null)}
          onDone={(success, msg) => {
            setResetTarget(null)
            showToast(msg, success ? 'success' : 'error')
          }}
        />
      )}

      {/* Edit profile modal */}
      {editTarget && (
        <EditUserModal
          target={editTarget}
          isMe={me?.id === editTarget.id}
          onClose={() => setEditTarget(null)}
          onSaved={(msg) => {
            setEditTarget(null)
            showToast(msg, 'success')
            reload()
          }}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}

      {/* Generic confirm modal */}
      {confirm && (
        <ConfirmModal
          title={confirm.title}
          message={confirm.message}
          onCancel={() => setConfirm(null)}
          onConfirm={async () => {
            await confirm.onConfirm()
            setConfirm(null)
          }}
        />
      )}
    </div>
  )
}

// ────────────────────────────────────────────────────────────────────────────
//  Sub-components
// ────────────────────────────────────────────────────────────────────────────

const IconButton: React.FC<{
  title: string
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
}> = ({ title, onClick, disabled, children }) => (
  <button
    title={title}
    onClick={onClick}
    disabled={disabled}
    className="p-2 rounded hover:bg-gray-100 disabled:opacity-30 disabled:cursor-not-allowed"
  >
    {children}
  </button>
)

const RoleBadge: React.FC<{ role: User['role'] }> = ({ role }) => {
  const map: Record<User['role'], string> = {
    ADMIN: 'bg-purple-100 text-purple-800',
    TECHNICIAN: 'bg-blue-100 text-blue-800',
    EMPLOYEE: 'bg-gray-100 text-gray-800',
  }
  const label = role === 'EMPLOYEE' ? 'USER' : role
  return (
    <span className={`px-2 py-1 rounded-full text-xs font-medium ${map[role]}`}>
      {label}
    </span>
  )
}

const StatusBadge: React.FC<{ status: User['status'] }> = ({ status }) => {
  const map: Record<User['status'], string> = {
    ACTIVE: 'bg-emerald-100 text-emerald-800',
    SUSPENDED: 'bg-orange-100 text-orange-800',
    INACTIVE: 'bg-red-100 text-red-800',
    PENDING: 'bg-yellow-100 text-yellow-800',
  }
  return (
    <span className={`px-2 py-1 rounded-full text-xs font-medium ${map[status]}`}>
      {status}
    </span>
  )
}

const ConfirmModal: React.FC<{
  title: string
  message: string
  onCancel: () => void
  onConfirm: () => void | Promise<void>
}> = ({ title, message, onCancel, onConfirm }) => (
  <ModalShell title={title} onClose={onCancel}>
    <p className="text-gray-700 mb-6">{message}</p>
    <div className="flex justify-end gap-2">
      <button
        onClick={onCancel}
        className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
      >
        Cancel
      </button>
      <button
        onClick={onConfirm}
        className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
      >
        Confirm
      </button>
    </div>
  </ModalShell>
)

const ModalShell: React.FC<{ title: string; onClose: () => void; children: React.ReactNode }> = ({
  title,
  onClose,
  children,
}) => (
  <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-bold text-gray-900">{title}</h2>
        <button onClick={onClose} className="p-1 rounded hover:bg-gray-100">
          <X className="h-4 w-4 text-gray-500" />
        </button>
      </div>
      {children}
    </div>
  </div>
)

const CreateUserModal: React.FC<{
  onClose: () => void
  onCreated: () => void
  onError: (msg: string) => void
}> = ({ onClose, onCreated, onError }) => {
  const [form, setForm] = useState<CreateUserRequest>({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    role: 'TECHNICIAN',
    department: '',
    phone: '',
  })
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await userAPI.adminCreate(form)
      onCreated()
    } catch (err: any) {
      onError(err.response?.data?.message || 'Could not create user')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title="Create user" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Field label="First name">
            <input
              required
              value={form.firstName}
              onChange={(e) => setForm({ ...form, firstName: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Last name">
            <input
              required
              value={form.lastName}
              onChange={(e) => setForm({ ...form, lastName: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <Field label="Email">
          <input
            required
            type="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <Field label="Temporary password">
          <input
            required
            type="text"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
          />
          <p className="text-xs text-gray-500 mt-1">
            Min 8 chars, with upper/lower/digit/special. Tell the user to change it after sign-in.
          </p>
        </Field>
        <Field label="Role">
          <select
            value={form.role}
            onChange={(e) => setForm({ ...form, role: e.target.value as CreateUserRequest['role'] })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          >
            <option value="EMPLOYEE">User (employee)</option>
            <option value="TECHNICIAN">Technician</option>
            <option value="ADMIN">Administrator</option>
          </select>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Department">
            <input
              value={form.department || ''}
              onChange={(e) => setForm({ ...form, department: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Phone">
            <input
              value={form.phone || ''}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Creating…' : 'Create user'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

/**
 * Create auth_db credentials for an email that already exists in user_db (legacy / directory-only users).
 */
const EnableSignInModal: React.FC<{
  onClose: () => void
  onDone: (success: boolean, msg: string) => void
  initialEmail?: string
}> = ({ onClose, onDone, initialEmail }) => {
  const [email, setEmail] = useState(initialEmail ?? '')
  const [pw, setPw] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (initialEmail !== undefined) {
      setEmail(initialEmail)
    }
  }, [initialEmail])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await authAPI.enableDirectoryLogin(email.trim().toLowerCase(), pw)
      onDone(true, 'Sign-in enabled. The user can log in with this password.')
    } catch (err: any) {
      onDone(false, err.response?.data?.message || 'Could not enable sign-in')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title="Enable sign-in (directory user)" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <p className="text-sm text-gray-600">
          Use this when someone appears in Manage Users but gets &quot;invalid email or password&quot; on the login page.
          Prefer the purple <strong>Enable sign-in</strong> icon on that user&apos;s row so the email is filled in
          correctly (typos break the flow). This creates credentials in auth with the same user ID as the directory.
        </p>
        <Field label="Email">
          <input
            required
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <Field label="Initial password">
          <input
            required
            type="password"
            value={pw}
            onChange={(e) => setPw(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono"
          />
          <p className="text-xs text-gray-500 mt-1">Min 8 characters, uppercase, lowercase, digit, and special character.</p>
        </Field>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Saving…' : 'Enable sign-in'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

const ResetPasswordModal: React.FC<{
  target: User
  onClose: () => void
  onDone: (success: boolean, msg: string) => void
}> = ({ target, onClose, onDone }) => {
  const [pw, setPw] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      await authAPI.adminResetPassword(target.id, pw)
      onDone(true, `Password reset for ${target.email}`)
    } catch (err: any) {
      onDone(false, err.response?.data?.message || 'Password reset failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title={`Reset password — ${target.email}`} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <div className="flex items-start gap-2 p-3 bg-amber-50 border border-amber-200 rounded text-sm text-amber-900">
          <ShieldCheck className="h-4 w-4 mt-0.5 shrink-0" />
          <span>
            The user will be signed out of any active sessions on next request and must use this
            new password to log in.
          </span>
        </div>
        <Field label="New password">
          <input
            required
            value={pw}
            onChange={(e) => setPw(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono"
          />
          <p className="text-xs text-gray-500 mt-1">
            Min 8 chars, with upper/lower/digit/special.
          </p>
        </Field>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Resetting…' : 'Reset password'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

const Field: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <label className="block">
    <span className="text-sm font-medium text-gray-700 mb-1 block">{label}</span>
    {children}
  </label>
)

/**
 * Full profile editor for ADMIN. Issues separate calls so each kind of change
 * goes through its own validated endpoint:
 *   - role change → PATCH /users/{id}/role
 *   - email change → PATCH /users/{id}/email (validates uniqueness + auth sync)
 *   - everything else (name/dept/phone/status) → PUT /users/{id}
 */
const EditUserModal: React.FC<{
  target: User
  isMe: boolean
  onClose: () => void
  onSaved: (msg: string) => void
  onError: (msg: string) => void
}> = ({ target, isMe, onClose, onSaved, onError }) => {
  const [firstName, setFirstName] = useState(target.firstName || '')
  const [lastName, setLastName] = useState(target.lastName || '')
  const [email, setEmail] = useState(target.email || '')
  const [role, setRole] = useState<User['role']>(target.role)
  const [status, setStatus] = useState<User['status']>(target.status)
  const [department, setDepartment] = useState(target.department || '')
  const [phone, setPhone] = useState(target.phone || '')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      // 1) Role change first — has its own last-admin guard server-side.
      if (role !== target.role) {
        await userAPI.changeRole(target.id, role)
      }
      // 2) Email change — has uniqueness + auth-service sync.
      const trimmedEmail = email.trim().toLowerCase()
      if (trimmedEmail && trimmedEmail !== (target.email || '').toLowerCase()) {
        await userAPI.changeEmail(target.id, trimmedEmail)
      }
      // 3) Everything else.
      const patch: UpdateUserRequest = {}
      if (firstName !== target.firstName) patch.firstName = firstName
      if (lastName !== target.lastName) patch.lastName = lastName
      if (department !== (target.department || '')) patch.department = department
      if (phone !== (target.phone || '')) patch.phone = phone
      if (status !== target.status) patch.status = status
      if (Object.keys(patch).length > 0) {
        await userAPI.update(target.id, patch)
      }
      onSaved(`Profile updated for ${trimmedEmail || target.email}`)
    } catch (err: any) {
      onError(err.response?.data?.message || 'Could not save user changes')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title={`Edit user — ${target.email}`} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <div className="grid grid-cols-2 gap-3">
          <Field label="First name">
            <input
              required
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Last name">
            <input
              required
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <Field label="Email">
          <input
            required
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
          <p className="text-xs text-gray-500 mt-1">
            Changing the email syncs to the login store; the user will sign in with the new address.
          </p>
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Role">
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as User['role'])}
              disabled={isMe}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
            >
              <option value="EMPLOYEE">User (employee)</option>
              <option value="TECHNICIAN">Technician</option>
              <option value="ADMIN">Administrator</option>
            </select>
            {isMe && <p className="text-xs text-gray-500 mt-1">You cannot change your own role.</p>}
          </Field>
          <Field label="Status">
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as User['status'])}
              disabled={isMe}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
            >
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
              <option value="INACTIVE">Inactive</option>
              <option value="PENDING">Pending</option>
            </select>
          </Field>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Department">
            <input
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Phone">
            <input
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

export default AdminUsers
