import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { NotificationProvider } from './context/NotificationContext'
import ToastContainer from './components/Common/ToastContainer'
import ProtectedRoute from './components/ProtectedRoute'
import Layout from './components/Layout/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import AdminDashboard from './pages/AdminDashboard'
import TechnicianDashboard from './pages/TechnicianDashboard'
import Tickets from './pages/Tickets'
import CreateTicket from './pages/CreateTicket'
import TicketDetails from './pages/TicketDetails'
import Users from './pages/Users'
import AdminUsers from './pages/AdminUsers'
import Members from './pages/Members'
import Assets from './pages/Assets'
import Profile from './pages/Profile'
import RootRedirect from './components/RootRedirect'

function App() {
  return (
    <Router>
      <AuthProvider>
        <NotificationProvider>
          <ToastContainer />
          <Routes>
            {/* Public Routes — public registration removed; admins provision all accounts. */}
            <Route path="/login" element={<Login />} />
            {/* Old /register link still in the wild → bounce to login. */}
            <Route path="/register" element={<Navigate to="/login" replace />} />

            {/* Protected Routes */}
            <Route
              path="/"
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route path="dashboard" element={<Dashboard />} />
              <Route
                path="user/dashboard"
                element={
                  <ProtectedRoute requiredRole={['EMPLOYEE']}>
                    <Dashboard />
                  </ProtectedRoute>
                }
              />
              <Route path="admin" element={<Navigate to="/admin/dashboard" replace />} />
              <Route
                path="admin/dashboard"
                element={
                  <ProtectedRoute requiredRole={['ADMIN']}>
                    <AdminDashboard />
                  </ProtectedRoute>
                }
              />
              <Route path="technician" element={<Navigate to="/technician/dashboard" replace />} />
              <Route
                path="technician/dashboard"
                element={
                  <ProtectedRoute requiredRole={['TECHNICIAN', 'ADMIN']}>
                    <TechnicianDashboard />
                  </ProtectedRoute>
                }
              />
              <Route path="tickets" element={<Tickets />} />
              <Route path="tickets/create" element={<CreateTicket />} />
              <Route path="tickets/:id" element={<TicketDetails />} />
              <Route path="users" element={<Users />} />
              {/* Team directory — visible to every authenticated role. */}
              <Route path="members" element={<Members />} />
              <Route
                path="admin/users"
                element={
                  <ProtectedRoute requiredRole={['ADMIN']}>
                    <AdminUsers />
                  </ProtectedRoute>
                }
              />
              <Route
                path="assets"
                element={
                  <ProtectedRoute requiredRole={['ADMIN']}>
                    <Assets />
                  </ProtectedRoute>
                }
              />
              <Route path="profile" element={<Profile />} />
              <Route index element={<RootRedirect />} />
            </Route>

            {/* Catch-all: unauthenticated → login; authenticated → role home */}
            <Route path="*" element={<RootRedirect />} />
          </Routes>
        </NotificationProvider>
      </AuthProvider>
    </Router>
  )
}

export default App
