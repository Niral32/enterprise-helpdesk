import React, { useEffect } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from './Common/LoadingSpinner'

interface ProtectedRouteProps {
  children: React.ReactNode
  requiredRole?: string[]
}

/**
 * Guard for authenticated routes.
 *
 * Three lines of defence against "back-button shows the dashboard after logout":
 *   1. localStorage is checked synchronously on every render — once logout
 *      clears it, the redirect to /login is immediate.
 *   2. The bfcache `pageshow` event is intercepted (see useEffect below). When
 *      the browser restores a cached page, we force a state refresh so the
 *      stale snapshot can't survive a back/forward navigation after sign-out.
 *   3. nginx serves index.html with `Cache-Control: no-store`, so a network
 *      back-navigation re-fetches the SPA shell.
 */
const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children, requiredRole }) => {
  const { isAuthenticated, isLoading, user, logout } = useAuth()
  const location = useLocation()

  useEffect(() => {
    const onPageShow = (e: PageTransitionEvent) => {
      // bfcache restored — re-validate auth synchronously against storage.
      if (e.persisted && !localStorage.getItem('token')) {
        // Force a full reload so the SPA re-mounts with cleared state.
        window.location.replace('/login')
      }
    }
    window.addEventListener('pageshow', onPageShow)
    return () => window.removeEventListener('pageshow', onPageShow)
  }, [])

  if (isLoading) {
    return <LoadingSpinner />
  }

  // Belt-and-braces: if context says authenticated but storage is empty,
  // somebody tampered with localStorage or another tab logged out — drop.
  const tokenInStorage = typeof window !== 'undefined' && localStorage.getItem('token')
  if (!isAuthenticated || !tokenInStorage) {
    if (isAuthenticated) logout() // cleans context state
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  if (requiredRole && user && !requiredRole.includes(user.role)) {
    // Role mismatch → bounce to the user's correct home (RootRedirect handles it).
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}

export default ProtectedRoute
