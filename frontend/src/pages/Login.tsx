import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { authAPI } from '../api/auth'
import { useNotification } from '../context/NotificationContext'
import { getApiErrorMessage } from '../utils/apiError'
import { normalizeEmail } from '../utils/email'
import { BarChart3, Mail, Lock, Eye, EyeOff } from 'lucide-react'

type LoginPortal = 'user' | 'staff'

const Login: React.FC = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [isLoading, setIsLoading] = useState(false)
  const [portal, setPortal] = useState<LoginPortal>('user')
  const navigate = useNavigate()
  const { setSessionFromAuthResponse } = useAuth()
  const { showToast } = useNotification()

  /** Route by role. Both portal tabs accept any role but route to the right dashboard. */
  const destinationForRole = (role: string): string | null => {
    switch (role) {
      case 'EMPLOYEE':
        return '/user/dashboard'
      case 'ADMIN':
        return '/admin/dashboard'
      case 'TECHNICIAN':
        return '/technician/dashboard'
      default:
        showToast('Unknown account role. Contact administrator.', 'error')
        return null
    }
  }

  /**
   * Translate auth-service errors into messages that point at the actual
   * recovery path. The most common newcomer-confusion modes are: account
   * exists in user_db but not auth_db (needs "Enable sign-in"), account is
   * SUSPENDED/INACTIVE, or simply wrong password.
   */
  const friendlyLoginError = (error: unknown): string => {
    const fallback = getApiErrorMessage(error)
    const status = (error as any)?.response?.status
    const serverMsg = (error as any)?.response?.data?.message as string | undefined
    if (status === 403 && serverMsg) {
      // Server already returned a tailored message (blocked / not activated).
      return serverMsg
    }
    if (status === 401 || (serverMsg && /invalid email or password/i.test(serverMsg))) {
      return 'Invalid email or password. If this is a new account, register first or ask an administrator to enable sign-in.'
    }
    return fallback
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)

    try {
      const { data } = await authAPI.login({
        email: normalizeEmail(email),
        password,
      })

      // Portal/role mismatch: warn but still let them in to the right dashboard.
      const expectedRoles = portal === 'user' ? ['EMPLOYEE'] : ['ADMIN', 'TECHNICIAN']
      if (!expectedRoles.includes(data.role)) {
        showToast(
          `This account is a ${data.role}; redirecting to the matching dashboard.`,
          'info'
        )
      }

      const path = destinationForRole(data.role)
      if (path) {
        setSessionFromAuthResponse(data)
        if (expectedRoles.includes(data.role)) {
          showToast('Login successful!', 'success')
        }
        navigate(path)
      }
    } catch (error: unknown) {
      showToast(friendlyLoginError(error), 'error')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-900 to-primary-700 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="text-center mb-8">
          <div className="flex justify-center mb-4">
            <div className="bg-white p-3 rounded-lg">
              <BarChart3 className="h-8 w-8 text-primary-900" />
            </div>
          </div>
          <h1 className="text-3xl font-bold text-white mb-2">Helpdesk System</h1>
          <p className="text-primary-100">IT Support & Asset Management</p>
        </div>

        <div className="bg-white rounded-lg shadow-lg p-8">
          <div className="flex rounded-lg border border-gray-200 p-1 mb-6 bg-gray-50">
            <button
              type="button"
              onClick={() => setPortal('user')}
              className={`flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors ${
                portal === 'user' ? 'bg-white shadow text-primary-800' : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              User Login
            </button>
            <button
              type="button"
              onClick={() => setPortal('staff')}
              className={`flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors ${
                portal === 'staff' ? 'bg-white shadow text-primary-800' : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              Admin / Technician Login
            </button>
          </div>

          <h2 className="text-xl font-bold text-gray-800 mb-6">
            {portal === 'user' ? 'Sign in as user' : 'Sign in as staff'}
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Email Address
              </label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 h-5 w-5 text-gray-400" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="you@example.com"
                  className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Password
              </label>
              <div className="relative">
                <Lock className="absolute left-3 top-3 h-5 w-5 text-gray-400 pointer-events-none" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  autoComplete="current-password"
                  className="w-full pl-10 pr-11 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 rounded-md text-gray-500 hover:text-gray-800 hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={0}
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="w-full bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 rounded-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

        </div>
      </div>
    </div>
  )
}

export default Login
