import React from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import LoadingSpinner from './Common/LoadingSpinner'

/**
 * Sends users to login if not authenticated; otherwise to the primary dashboard for their role.
 * Use for `/` and `*` so unknown URLs never assume an open session without a checked JWT.
 */
const RootRedirect: React.FC = () => {
  const { isAuthenticated, isLoading, user } = useAuth()

  if (isLoading) {
    return <LoadingSpinner />
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  switch (user?.role) {
    case 'ADMIN':
      return <Navigate to="/admin/dashboard" replace />
    case 'TECHNICIAN':
      return <Navigate to="/technician/dashboard" replace />
    case 'EMPLOYEE':
      return <Navigate to="/user/dashboard" replace />
    default:
      return <Navigate to="/dashboard" replace />
  }
}

export default RootRedirect
