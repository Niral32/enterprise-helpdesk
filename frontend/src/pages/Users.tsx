import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Settings2 } from 'lucide-react'
import { userAPI } from '../api/users'
import { User } from '../types'
import { useNotification } from '../context/NotificationContext'
import { useAuth } from '../context/AuthContext'

const Users: React.FC = () => {
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const { showToast } = useNotification()
  const { user: me } = useAuth()

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const { data } = await userAPI.getAll()
        if (!cancelled) setUsers(data)
      } catch {
        if (!cancelled) showToast('Could not load users', 'error')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [showToast])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-bold text-gray-900">User Management</h1>
        {me?.role === 'ADMIN' ? (
          <Link
            to="/admin/users"
            className="flex items-center space-x-2 bg-primary-600 hover:bg-primary-700 text-white px-4 py-2 rounded-lg transition-colors"
          >
            <Settings2 className="h-5 w-5" />
            <span>User admin (create &amp; lifecycle)</span>
          </Link>
        ) : null}
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-600">Loading users…</div>
        ) : users.length === 0 ? (
          <div className="p-8 text-center text-gray-600">No users in directory. Register an account to bootstrap profile rows.</div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Name</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Email</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Role</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Department</th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {[...users].sort((a, b) => b.id - a.id).map((user) => (
                <tr key={user.id} className="hover:bg-gray-50 transition-colors">
                  <td className="px-6 py-4 text-sm text-gray-900 font-medium">
                    {user.firstName} {user.lastName}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">{user.email}</td>
                  <td className="px-6 py-4 text-sm">
                    <span className="px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-medium">{user.role}</span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">{user.department ?? '—'}</td>
                  <td className="px-6 py-4 text-sm text-gray-600">{user.status}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}

export default Users
