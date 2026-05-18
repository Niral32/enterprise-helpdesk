import React, { useEffect, useRef, useState } from 'react'
import { Camera, Trash2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { userAPI } from '../api/users'
import { User } from '../types'
import { useNotification } from '../context/NotificationContext'
import Avatar from '../components/Common/Avatar'

const Profile: React.FC = () => {
  const { user: authUser, updateUserLocal } = useAuth()
  const { showToast } = useNotification()
  const [profile, setProfile] = useState<User | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [department, setDepartment] = useState('')
  const [phone, setPhone] = useState('')
  const [photoBusy, setPhotoBusy] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const { data } = await userAPI.getProfile()
        if (!cancelled) {
          setProfile(data)
          setFirstName(data.firstName)
          setLastName(data.lastName)
          setDepartment(data.department ?? '')
          setPhone(data.phone ?? '')
        }
      } catch {
        if (!cancelled && authUser) {
          setProfile({
            ...authUser,
            department: authUser.department ?? '',
            phone: authUser.phone ?? '',
          })
          setFirstName(authUser.firstName)
          setLastName(authUser.lastName)
          setDepartment(authUser.department ?? '')
          setPhone(authUser.phone ?? '')
          showToast('Using cached login profile — user directory may be unavailable.', 'info')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [authUser, showToast])

  const display = profile ?? authUser

  const onPickPhoto = () => fileInputRef.current?.click()

  const onPhotoSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setPhotoBusy(true)
    try {
      const { data } = await userAPI.uploadMyPhoto(file)
      setProfile(data)
      updateUserLocal({ profileImageUrl: data.profileImageUrl ?? null })
      showToast('Profile photo updated', 'success')
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Could not upload photo', 'error')
    } finally {
      setPhotoBusy(false)
      // Reset so selecting the same file again still fires onChange.
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const onRemovePhoto = async () => {
    if (!display?.profileImageUrl) return
    setPhotoBusy(true)
    try {
      const { data } = await userAPI.removeMyPhoto()
      setProfile(data)
      updateUserLocal({ profileImageUrl: null })
      showToast('Profile photo removed', 'success')
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Could not remove photo', 'error')
    } finally {
      setPhotoBusy(false)
    }
  }

  const handleSave = async () => {
    try {
      const { data } = await userAPI.updateProfile({
        firstName,
        lastName,
        department: department || undefined,
        phone: phone || undefined,
      })
      setProfile(data)
      setIsEditing(false)
      showToast('Profile updated', 'success')
    } catch {
      showToast('Could not update profile', 'error')
    }
  }

  if (loading && !display) {
    return (
      <div className="max-w-2xl">
        <p className="text-gray-600">Loading profile…</p>
      </div>
    )
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-3xl font-bold text-gray-900 mb-6">My Profile</h1>

      <div className="bg-white rounded-lg shadow">
        <div className="p-6 border-b border-gray-200">
          <div className="flex items-center space-x-4">
            <div className="relative">
              <Avatar
                firstName={display?.firstName}
                lastName={display?.lastName}
                imageUrl={display?.profileImageUrl}
                size={80}
                className="text-2xl"
              />
              <button
                type="button"
                onClick={onPickPhoto}
                disabled={photoBusy}
                title="Change profile photo"
                className="absolute -bottom-1 -right-1 bg-primary-600 text-white rounded-full p-1.5 shadow hover:bg-primary-700 disabled:opacity-50"
              >
                <Camera className="h-4 w-4" />
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/jpeg,image/jpg,image/png,image/gif,image/webp"
                className="hidden"
                onChange={onPhotoSelected}
              />
            </div>
            <div className="flex-1">
              <h2 className="text-2xl font-bold text-gray-900">
                {display?.firstName} {display?.lastName}
              </h2>
              <p className="text-gray-600">{display?.role}</p>
              {display?.profileImageUrl && (
                <button
                  type="button"
                  onClick={() => void onRemovePhoto()}
                  disabled={photoBusy}
                  className="mt-1 text-xs text-red-600 hover:underline disabled:opacity-50 inline-flex items-center gap-1"
                >
                  <Trash2 className="h-3 w-3" /> Remove photo
                </button>
              )}
            </div>
          </div>
        </div>

        <div className="p-6">
          <div className="space-y-6">
            <div className="grid grid-cols-2 gap-6">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">First Name</label>
                <input
                  type="text"
                  disabled={!isEditing}
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 disabled:bg-gray-50 disabled:text-gray-600"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Last Name</label>
                <input
                  type="text"
                  disabled={!isEditing}
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 disabled:bg-gray-50 disabled:text-gray-600"
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Email</label>
              <input
                type="email"
                disabled
                value={display?.email ?? ''}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-600"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Department</label>
              <input
                type="text"
                disabled={!isEditing}
                value={department}
                onChange={(e) => setDepartment(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 disabled:bg-gray-50 disabled:text-gray-600"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Phone</label>
              <input
                type="text"
                disabled={!isEditing}
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 disabled:bg-gray-50 disabled:text-gray-600"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Role</label>
              <input
                type="text"
                disabled
                value={display?.role ?? ''}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-gray-50 text-gray-600"
              />
            </div>

            <div className="pt-4">
              {!isEditing ? (
                <button
                  type="button"
                  onClick={() => setIsEditing(true)}
                  className="bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 px-6 rounded-lg transition-colors"
                >
                  Edit Profile
                </button>
              ) : (
                <div className="flex space-x-3">
                  <button
                    type="button"
                    onClick={() => void handleSave()}
                    className="bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 px-6 rounded-lg transition-colors"
                  >
                    Save Changes
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setIsEditing(false)
                      if (profile) {
                        setFirstName(profile.firstName)
                        setLastName(profile.lastName)
                        setDepartment(profile.department ?? '')
                        setPhone(profile.phone ?? '')
                      }
                    }}
                    className="border border-gray-300 text-gray-700 font-medium py-2 px-6 rounded-lg hover:bg-gray-50 transition-colors"
                  >
                    Cancel
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Profile
