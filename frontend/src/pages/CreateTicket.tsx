import React, { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Paperclip, Trash2 } from 'lucide-react'
import { useNotification } from '../context/NotificationContext'
import { useAuth } from '../context/AuthContext'
import { ticketAPI } from '../api/tickets'
import { assetAPI } from '../api/assets'
import { Asset, TicketPriority } from '../types'

const ALLOWED_UPLOAD_TYPES = [
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/gif',
  'image/webp',
  'application/pdf',
]
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024
const MAX_UPLOAD_FILES = 10

/**
 * Sentinel value for the "no specific asset / other" radio option. We use a
 * string here so the radio group can compare with `value` directly; submit
 * code converts it back to `undefined` for the request payload.
 */
const ASSET_OTHER = 'OTHER'

const CreateTicket: React.FC = () => {
  const navigate = useNavigate()
  const { showToast } = useNotification()
  const { user } = useAuth()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [category, setCategory] = useState('Hardware')

  // Location
  const [building, setBuilding] = useState('')
  const [locationDepartment, setLocationDepartment] = useState('')
  const [roomNumber, setRoomNumber] = useState('')
  const [locationNotes, setLocationNotes] = useState('')

  // Assets assigned to me. Empty when the user has no devices on record —
  // the radio group then degrades to just "Other / Not Listed".
  const [myAssets, setMyAssets] = useState<Asset[]>([])
  const [assetsLoading, setAssetsLoading] = useState(true)
  const [selectedAssetKey, setSelectedAssetKey] = useState<string>(ASSET_OTHER)

  const [isLoading, setIsLoading] = useState(false)

  // Files staged in the browser; uploaded after ticket creation in handleSubmit.
  const [stagedFiles, setStagedFiles] = useState<File[]>([])
  const fileInputRef = useRef<HTMLInputElement>(null)

  const addFiles = (incoming: FileList | null) => {
    if (!incoming || incoming.length === 0) return
    const accepted: File[] = []
    const rejected: string[] = []
    for (const f of Array.from(incoming)) {
      if (!ALLOWED_UPLOAD_TYPES.includes(f.type)) {
        rejected.push(`${f.name} (unsupported type)`)
        continue
      }
      if (f.size > MAX_UPLOAD_BYTES) {
        rejected.push(`${f.name} (over 10 MB)`)
        continue
      }
      if (stagedFiles.length + accepted.length >= MAX_UPLOAD_FILES) {
        rejected.push(`${f.name} (limit ${MAX_UPLOAD_FILES} files)`)
        continue
      }
      accepted.push(f)
    }
    if (accepted.length) setStagedFiles((prev) => [...prev, ...accepted])
    if (rejected.length) showToast(`Skipped: ${rejected.join(', ')}`, 'warning')
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const removeFile = (idx: number) => {
    setStagedFiles((prev) => prev.filter((_, i) => i !== idx))
  }

  useEffect(() => {
    if (!user?.id) {
      setAssetsLoading(false)
      return
    }
    let cancelled = false
    assetAPI
      .getByAssignedTo(user.id)
      .then((r) => {
        if (cancelled) return
        setMyAssets(r.data || [])
      })
      .catch(() => {
        // Non-fatal: user can still file a ticket without selecting an asset.
        if (!cancelled) setMyAssets([])
      })
      .finally(() => {
        if (!cancelled) setAssetsLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [user?.id])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsLoading(true)
    try {
      const linkedAssetId =
        selectedAssetKey !== ASSET_OTHER && /^\d+$/.test(selectedAssetKey)
          ? Number(selectedAssetKey)
          : undefined
      const created = await ticketAPI.create({
        title,
        description,
        priority,
        category,
        linkedAssetId,
        building: building.trim() || undefined,
        locationDepartment: locationDepartment.trim() || undefined,
        roomNumber: roomNumber.trim() || undefined,
        locationNotes: locationNotes.trim() || undefined,
      })

      // Upload any staged files now that we have a ticket id. Failures here
      // don't undo the ticket creation — we surface a partial-success toast.
      if (stagedFiles.length > 0) {
        let failed = 0
        for (const file of stagedFiles) {
          try {
            await ticketAPI.uploadAttachment(created.data.id, file)
          } catch {
            failed++
          }
        }
        if (failed > 0) {
          showToast(`Ticket created, but ${failed} attachment(s) failed to upload`, 'warning')
        } else {
          showToast('Ticket and attachments created successfully', 'success')
        }
      } else {
        showToast('Ticket created successfully', 'success')
      }
      navigate('/tickets')
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Failed to create ticket', 'error')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="text-3xl font-bold text-gray-900 mb-6">Create New Ticket</h1>

      <div className="bg-white rounded-lg shadow p-6">
        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Title</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Brief description of the issue"
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Detailed description of the issue"
              rows={6}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Priority</label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value as TicketPriority)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Category</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="Hardware">Hardware</option>
                <option value="Software">Software</option>
                <option value="Network">Network</option>
                <option value="Access Request">Access Request</option>
                <option value="Printer">Printer</option>
                <option value="Security">Security</option>
                <option value="Other">Other</option>
              </select>
            </div>
          </div>

          {/* Problem device — radio list of my assets + Other */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">Problem Device</label>
            {assetsLoading ? (
              <p className="text-sm text-gray-500">Loading your assigned devices…</p>
            ) : (
              <div className="space-y-2 border border-gray-200 rounded-lg p-3 bg-gray-50">
                {myAssets.length === 0 && (
                  <p className="text-xs text-gray-500">
                    You have no devices on record. Choose &quot;Other / Not Listed&quot; and describe
                    the device in the ticket description.
                  </p>
                )}
                {myAssets.map((a) => (
                  <label key={a.id} className="flex items-start gap-2 cursor-pointer">
                    <input
                      type="radio"
                      name="asset"
                      value={String(a.id)}
                      checked={selectedAssetKey === String(a.id)}
                      onChange={(e) => setSelectedAssetKey(e.target.value)}
                      className="mt-1"
                    />
                    <span className="text-sm">
                      <span className="block font-medium text-gray-900">{a.name}</span>
                      <span className="block text-xs text-gray-600">
                        {a.assetType}
                        {a.serialNumber ? ` · SN ${a.serialNumber}` : ''}
                        {` · ${a.status}`}
                      </span>
                    </span>
                  </label>
                ))}
                <label className="flex items-center gap-2 cursor-pointer pt-1">
                  <input
                    type="radio"
                    name="asset"
                    value={ASSET_OTHER}
                    checked={selectedAssetKey === ASSET_OTHER}
                    onChange={(e) => setSelectedAssetKey(e.target.value)}
                  />
                  <span className="text-sm text-gray-800">Other / Not Listed</span>
                </label>
              </div>
            )}
          </div>

          {/* Device location — all optional */}
          <fieldset className="border border-gray-200 rounded-lg p-4">
            <legend className="px-2 text-sm font-medium text-gray-700">
              Device Location <span className="text-gray-400">(optional)</span>
            </legend>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Building</label>
                <input
                  type="text"
                  value={building}
                  onChange={(e) => setBuilding(e.target.value)}
                  placeholder="e.g. A Block"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Department</label>
                <input
                  type="text"
                  value={locationDepartment}
                  onChange={(e) => setLocationDepartment(e.target.value)}
                  placeholder="e.g. IT Lab"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">Room / Office</label>
                <input
                  type="text"
                  value={roomNumber}
                  onChange={(e) => setRoomNumber(e.target.value)}
                  placeholder="e.g. 204"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-gray-600 mb-1">
                  Location notes
                </label>
                <input
                  type="text"
                  value={locationNotes}
                  onChange={(e) => setLocationNotes(e.target.value)}
                  placeholder="e.g. desk behind printer"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                />
              </div>
            </div>
          </fieldset>

          {/* Attachments — staged in the browser, uploaded after ticket is created */}
          <fieldset className="border border-gray-200 rounded-lg p-4">
            <legend className="px-2 text-sm font-medium text-gray-700">
              Attachments <span className="text-gray-400">(optional, max 10 files / 10 MB each)</span>
            </legend>
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="inline-flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-sm hover:bg-gray-50"
                >
                  <Paperclip className="h-4 w-4" /> Choose files
                </button>
                <span className="text-xs text-gray-500">JPG, PNG, GIF, WEBP, PDF</span>
              </div>
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept={ALLOWED_UPLOAD_TYPES.join(',')}
                className="hidden"
                onChange={(e) => addFiles(e.target.files)}
              />
              {stagedFiles.length > 0 && (
                <ul className="grid grid-cols-2 md:grid-cols-3 gap-2">
                  {stagedFiles.map((f, i) => {
                    const isImage = f.type.startsWith('image/')
                    const url = isImage ? URL.createObjectURL(f) : null
                    return (
                      <li
                        key={`${f.name}-${i}`}
                        className="relative border border-gray-200 rounded-lg overflow-hidden bg-gray-50 group"
                      >
                        {isImage && url ? (
                          <img
                            src={url}
                            alt={f.name}
                            className="w-full h-24 object-cover"
                            onLoad={() => URL.revokeObjectURL(url)}
                          />
                        ) : (
                          <div className="w-full h-24 flex items-center justify-center text-xs text-gray-500 px-2 text-center">
                            {f.name}
                          </div>
                        )}
                        <div className="px-2 py-1 text-[10px] text-gray-600 truncate" title={f.name}>
                          {f.name} · {Math.round(f.size / 1024)} KB
                        </div>
                        <button
                          type="button"
                          onClick={() => removeFile(i)}
                          className="absolute top-1 right-1 bg-white/90 hover:bg-red-50 text-red-600 rounded-full p-1 shadow opacity-0 group-hover:opacity-100 transition"
                          title="Remove"
                        >
                          <Trash2 className="h-3 w-3" />
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          </fieldset>

          <div className="flex space-x-4">
            <button
              type="submit"
              disabled={isLoading}
              className="flex-1 bg-primary-600 hover:bg-primary-700 text-white font-medium py-2 rounded-lg transition-colors disabled:opacity-50"
            >
              {isLoading ? 'Creating...' : 'Create Ticket'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/tickets')}
              className="flex-1 border border-gray-300 text-gray-700 font-medium py-2 rounded-lg hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

export default CreateTicket
