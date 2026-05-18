import React from 'react'

interface LoadingSpinnerProps {
  message?: string
  size?: 'sm' | 'md' | 'lg'
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({ message = 'Loading...', size = 'md' }) => {
  const sizeClasses = {
    sm: 'h-4 w-4',
    md: 'h-8 w-8',
    lg: 'h-12 w-12',
  }

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="text-center">
        <div className={`${sizeClasses[size]} border-4 border-primary-500 border-t-transparent rounded-full animate-spin mx-auto mb-4`}></div>
        <p className="text-gray-600 text-sm">{message}</p>
      </div>
    </div>
  )
}

export default LoadingSpinner
