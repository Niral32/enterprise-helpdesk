import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import { BarChart3, Ticket, Users, Zap, Settings, LogOut, ShieldCheck, Wrench, Contact } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'

interface SidebarProps {
  isOpen: boolean
}

const Sidebar: React.FC<SidebarProps> = ({ isOpen }) => {
  const location = useLocation()
  const { logout, user } = useAuth()

  const isActive = (path: string) => location.pathname === path || location.pathname.startsWith(path + '/')

  const dashboardHref = user?.role === 'EMPLOYEE' ? '/user/dashboard' : '/dashboard'

  const navItems = [
    { path: dashboardHref, label: 'Dashboard', icon: BarChart3 },
    ...(user?.role === 'ADMIN' ? [{ path: '/admin/dashboard', label: 'Admin', icon: ShieldCheck }] : []),
    ...(user?.role === 'TECHNICIAN' || user?.role === 'ADMIN'
      ? [{ path: '/technician/dashboard', label: 'My Queue', icon: Wrench }]
      : []),
    { path: '/tickets', label: 'Tickets', icon: Ticket },
    // Team directory — everyone (admin/technician/employee) can see who's
    // in the org with profile photos. Admin still has the lifecycle controls
    // under Manage Users.
    { path: '/members', label: 'Members', icon: Contact },
    ...(user?.role === 'ADMIN'
      ? [{ path: '/admin/users', label: 'Manage Users', icon: Users }]
      : user?.role === 'TECHNICIAN'
      ? [{ path: '/users', label: 'Users', icon: Users }]
      : []),
    ...(user?.role === 'ADMIN' ? [{ path: '/assets', label: 'Assets', icon: Zap }] : []),
  ]

  return (
    <aside className={`${isOpen ? 'w-64' : 'w-20'} bg-primary-900 text-white transition-all duration-300 flex flex-col`}>
      {/* Logo */}
      <div className="p-4 border-b border-primary-800 flex items-center justify-center">
        <BarChart3 className="h-8 w-8" />
        {isOpen && <span className="ml-3 font-bold text-lg">Helpdesk</span>}
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-4 space-y-2">
        {navItems.map((item) => {
          const Icon = item.icon
          return (
            <Link
              key={item.path}
              to={item.path}
              className={`flex items-center p-3 rounded-lg transition-colors ${
                isActive(item.path)
                  ? 'bg-primary-700 text-white'
                  : 'text-primary-200 hover:bg-primary-800'
              }`}
            >
              <Icon className="h-5 w-5" />
              {isOpen && <span className="ml-3">{item.label}</span>}
            </Link>
          )
        })}
      </nav>

      {/* User Profile & Logout */}
      <div className="p-4 border-t border-primary-800 space-y-2">
        <Link
          to="/profile"
          className={`flex items-center p-3 rounded-lg transition-colors ${
            isActive('/profile')
              ? 'bg-primary-700 text-white'
              : 'text-primary-200 hover:bg-primary-800'
          }`}
        >
          <Settings className="h-5 w-5" />
          {isOpen && <span className="ml-3">Profile</span>}
        </Link>

        <button
          onClick={logout}
          className="w-full flex items-center p-3 rounded-lg text-primary-200 hover:bg-primary-800 transition-colors"
        >
          <LogOut className="h-5 w-5" />
          {isOpen && <span className="ml-3">Logout</span>}
        </button>
      </div>
    </aside>
  )
}

export default Sidebar
