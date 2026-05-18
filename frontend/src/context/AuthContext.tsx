import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { User, AuthResponse } from '../types'
import { authAPI } from '../api/auth'
import { userAPI } from '../api/users'

interface AuthContextType {
  user: User | null
  token: string | null
  isAuthenticated: boolean
  isLoading: boolean
  setSessionFromAuthResponse: (data: AuthResponse) => void
  updateUserLocal: (patch: Partial<User>) => void
  logout: () => void
  refreshToken: () => Promise<void>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

function clearStoredSession() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
}

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const navigate = useNavigate()
  const [user, setUser] = useState<User | null>(null)
  const [token, setToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  // Restore session from localStorage; validate JWT with auth-service so expired tokens cannot open the app.
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const storedToken = localStorage.getItem('token')
      const storedUser = localStorage.getItem('user')

      if (!storedToken || !storedUser) {
        if (!cancelled) setIsLoading(false)
        return
      }

      setToken(storedToken)
      setUser(JSON.parse(storedUser))

      try {
        const { data } = await authAPI.validateToken(storedToken)
        if (cancelled) return
        if (data !== true) {
          // Auth-service explicitly said this token is bad — drop the session.
          clearStoredSession()
          setToken(null)
          setUser(null)
        } else {
          // Token good — re-hydrate from user-service so display data
          // (name, avatar, role) reflects the directory, not the JWT issue
          // time. This catches drift after admin profile edits.
          void hydrateFromDirectory()
        }
      } catch (err: any) {
        if (cancelled) return
        // Only clear the session on a definitive auth rejection (401/403).
        // Network/5xx errors (auth-service briefly down, gateway restarting,
        // CORS hiccup) should leave the stored session intact so the user is
        // not silently logged out and forced to re-enter credentials on every
        // hiccup. The axios interceptor will refresh on the next 401 anyway.
        const status = err?.response?.status
        if (status === 401 || status === 403) {
          clearStoredSession()
          setToken(null)
          setUser(null)
        } else {
          // Keep the optimistic session; subsequent API calls will trigger
          // the refresh flow if the token is actually stale.
          console.warn('Token validation could not reach auth-service; keeping cached session.', err)
        }
      } finally {
        if (!cancelled) setIsLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const setSessionFromAuthResponse = (data: AuthResponse) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: data.userId,
        email: data.email,
        firstName: data.firstName,
        lastName: data.lastName,
        role: data.role,
        profileImageUrl: data.profileImageUrl ?? null,
      })
    )

    setToken(data.token)
    setUser({
      id: data.userId,
      email: data.email,
      firstName: data.firstName,
      lastName: data.lastName,
      role: data.role as User['role'],
      status: 'ACTIVE',
      profileImageUrl: data.profileImageUrl ?? null,
      createdAt: new Date().toISOString(),
    })

    // Refresh from the directory in the background. The login response
    // comes from auth_db; the canonical display name + avatar live in
    // user_db. Pulling /users/me here makes the header agree with the
    // Profile page even when the two stores have drifted.
    void hydrateFromDirectory()
  }

  /**
   * Best-effort fetch of the authoritative profile from user-service. Any
   * fresher name / department / phone / avatar overrides the JWT-derived
   * cache. Failures are silent — the JWT data is a reasonable fallback.
   */
  const hydrateFromDirectory = async () => {
    try {
      const { data: me } = await userAPI.getProfile()
      setUser((cur) =>
        cur
          ? {
              ...cur,
              firstName: me.firstName ?? cur.firstName,
              lastName: me.lastName ?? cur.lastName,
              department: me.department ?? cur.department,
              phone: me.phone ?? cur.phone,
              profileImageUrl: me.profileImageUrl ?? cur.profileImageUrl ?? null,
              status: me.status ?? cur.status,
              role: (me.role as User['role']) ?? cur.role,
            }
          : cur
      )
      try {
        const raw = localStorage.getItem('user')
        const stored = raw ? JSON.parse(raw) : {}
        localStorage.setItem(
          'user',
          JSON.stringify({
            ...stored,
            firstName: me.firstName,
            lastName: me.lastName,
            profileImageUrl: me.profileImageUrl ?? null,
            role: me.role,
          })
        )
      } catch {
        /* storage write best-effort */
      }
    } catch {
      /* directory unreachable — stick with the JWT-cached data */
    }
  }

  /**
   * Patch the cached User in storage + state when the user updates their
   * own profile (avatar, name, etc.). Keeps Header/Sidebar in sync without
   * requiring a full login refresh.
   */
  const updateUserLocal = (patch: Partial<User>) => {
    setUser((cur) => {
      if (!cur) return cur
      const next = { ...cur, ...patch }
      try {
        const raw = localStorage.getItem('user')
        const stored = raw ? JSON.parse(raw) : {}
        localStorage.setItem('user', JSON.stringify({ ...stored, ...patch }))
      } catch {
        /* storage write best-effort */
      }
      return next
    })
  }

  const logout = () => {
    clearStoredSession()
    setToken(null)
    setUser(null)
    // Hard reload so React state, in-flight requests, and the browser bfcache
    // are all dropped. A SPA navigate() leaves the previous component tree
    // mounted and the browser can still serve the dashboard from bfcache on
    // back-button presses. window.location.replace replaces the history entry
    // and forces a fresh fetch of index.html (which nginx now serves no-store).
    if (typeof window !== 'undefined') {
      window.location.replace('/login')
    } else {
      navigate('/login', { replace: true })
    }
  }

  const refreshToken = async () => {
    try {
      const refreshTokenValue = localStorage.getItem('refreshToken')
      if (!refreshTokenValue) {
        throw new Error('No refresh token found')
      }

      const response = await authAPI.refreshToken(refreshTokenValue)
      const data = response.data as AuthResponse

      localStorage.setItem('token', data.token)
      localStorage.setItem('refreshToken', data.refreshToken)
      setToken(data.token)
    } catch (error) {
      logout()
      throw error
    }
  }

  const value: AuthContextType = {
    user,
    token,
    isAuthenticated: !!token && !!user,
    isLoading,
    setSessionFromAuthResponse,
    updateUserLocal,
    logout,
    refreshToken,
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
