import React from 'react'
import { useNotification } from '../../context/NotificationContext'

/**
 * Renders toast notifications globally. Without this, {@link showToast} updates state but nothing appears on screen.
 */
const ToastContainer: React.FC = () => {
  const { toasts, removeToast } = useNotification()

  if (toasts.length === 0) return null

  return (
    <div
      className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 max-w-md pointer-events-none"
      aria-live="polite"
    >
      {toasts.map((toast) => {
        const bg =
          toast.type === 'success'
            ? 'bg-emerald-600'
            : toast.type === 'error'
              ? 'bg-red-600'
              : toast.type === 'warning'
                ? 'bg-amber-600'
                : 'bg-sky-600'
        return (
          <div
            key={toast.id}
            className={`pointer-events-auto flex items-start justify-between gap-3 rounded-lg px-4 py-3 text-sm font-medium text-white shadow-lg ${bg}`}
          >
            <span>{toast.message}</span>
            <button
              type="button"
              onClick={() => removeToast(toast.id)}
              className="shrink-0 opacity-80 hover:opacity-100"
              aria-label="Dismiss"
            >
              ×
            </button>
          </div>
        )
      })}
    </div>
  )
}

export default ToastContainer
