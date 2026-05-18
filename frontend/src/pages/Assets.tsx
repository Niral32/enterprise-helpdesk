import React, { useCallback, useEffect, useState } from 'react'
import { Plus, RefreshCw, X, Pencil, Trash2 } from 'lucide-react'
import { useNotification } from '../context/NotificationContext'
import { assetAPI, CreateAssetRequest, UpdateAssetRequest } from '../api/assets'
import { userAPI } from '../api/users'
import { Asset, AssetStatus, User } from '../types'

/** asset_db requires non-null assignedTo; backend treats 0 as unassigned pool. */
const UNASSIGNED = 0

/** Matches asset-service `Asset.AssetStatus` (valueOf on update). */
const STATUS_OPTIONS = ['AVAILABLE', 'ASSIGNED', 'IN_REPAIR', 'RETIRED', 'LOST'] as const

function statusBadgeClass(status: string): string {
  const u = status.toUpperCase()
  if (u === 'AVAILABLE') return 'bg-emerald-100 text-emerald-800'
  if (u === 'ASSIGNED') return 'bg-purple-100 text-purple-800'
  if (u === 'IN_REPAIR') return 'bg-amber-100 text-amber-800'
  if (u === 'RETIRED' || u === 'LOST') return 'bg-gray-200 text-gray-800'
  return 'bg-slate-100 text-slate-800'
}

const ModalShell: React.FC<{ title: string; onClose: () => void; children: React.ReactNode }> = ({
  title,
  onClose,
  children,
}) => (
  <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
    <div className="bg-white rounded-lg shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-bold text-gray-900">{title}</h2>
        <button type="button" onClick={onClose} className="p-1 rounded hover:bg-gray-100" aria-label="Close">
          <X className="h-4 w-4 text-gray-500" />
        </button>
      </div>
      {children}
    </div>
  </div>
)

const Field: React.FC<{ label: string; children: React.ReactNode }> = ({ label, children }) => (
  <label className="block">
    <span className="text-sm font-medium text-gray-700 mb-1 block">{label}</span>
    {children}
  </label>
)

const Assets: React.FC = () => {
  const { showToast } = useNotification()
  const [assets, setAssets] = useState<Asset[]>([])
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [editAsset, setEditAsset] = useState<Asset | null>(null)
  const [deleteId, setDeleteId] = useState<number | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [a, u] = await Promise.all([assetAPI.getAll(), userAPI.getAll()])
      setAssets(a.data)
      setUsers(u.data)
    } catch (e: unknown) {
      const msg =
        typeof e === 'object' && e !== null && 'response' in e
          ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      showToast(msg || 'Could not load assets', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    void load()
  }, [load])

  const userLabel = (id?: number) => {
    if (id === undefined || id === UNASSIGNED) return '—'
    const u = users.find((x) => x.id === id)
    return u ? `${u.firstName} ${u.lastName}` : `#${id}`
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <h1 className="text-3xl font-bold text-gray-900">Asset Management</h1>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => void load()}
            className="flex items-center gap-2 px-3 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            <RefreshCw className="h-4 w-4" /> Refresh
          </button>
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className="flex items-center gap-2 bg-primary-600 hover:bg-primary-700 text-white px-4 py-2 rounded-lg transition-colors"
          >
            <Plus className="h-5 w-5" />
            <span>Add Asset</span>
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-600">Loading assets…</div>
        ) : assets.length === 0 ? (
          <div className="p-8 text-center text-gray-600">No assets yet. Add one to get started.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px]">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Name</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Type</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Serial #</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Assigned to</th>
                  <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">Status</th>
                  <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {[...assets].sort((a, b) => b.id - a.id).map((asset) => (
                  <tr key={asset.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 text-sm text-gray-900 font-medium">{asset.name}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{asset.assetType}</td>
                    <td className="px-6 py-4 text-sm text-gray-600 font-mono">{asset.serialNumber ?? '—'}</td>
                    <td className="px-6 py-4 text-sm text-gray-600">{userLabel(asset.assignedTo)}</td>
                    <td className="px-6 py-4 text-sm">
                      <span
                        className={`px-3 py-1 rounded-full text-xs font-medium ${statusBadgeClass(asset.status)}`}
                      >
                        {asset.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-right space-x-3 whitespace-nowrap">
                      <button
                        type="button"
                        onClick={() => setEditAsset(asset)}
                        className="text-primary-600 hover:text-primary-700 font-medium inline-flex items-center gap-1"
                      >
                        <Pencil className="h-3.5 w-3.5" /> Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => setDeleteId(asset.id)}
                        className="text-red-600 hover:text-red-700 font-medium inline-flex items-center gap-1"
                      >
                        <Trash2 className="h-3.5 w-3.5" /> Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {createOpen && (
        <CreateAssetModal
          users={users}
          onClose={() => setCreateOpen(false)}
          onSaved={() => {
            setCreateOpen(false)
            void load()
            showToast('Asset created', 'success')
          }}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}

      {editAsset && (
        <EditAssetModal
          asset={editAsset}
          users={users}
          onClose={() => setEditAsset(null)}
          onSaved={() => {
            setEditAsset(null)
            void load()
            showToast('Asset updated', 'success')
          }}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}

      {deleteId !== null && (
        <ModalShell title="Delete asset?" onClose={() => setDeleteId(null)}>
          <p className="text-gray-700 mb-6">This cannot be undone.</p>
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setDeleteId(null)}
              className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={async () => {
                try {
                  await assetAPI.delete(deleteId)
                  setDeleteId(null)
                  void load()
                  showToast('Asset deleted', 'success')
                } catch (e: unknown) {
                  const msg =
                    typeof e === 'object' && e !== null && 'response' in e
                      ? (e as { response?: { data?: { message?: string } } }).response?.data?.message
                      : undefined
                  showToast(msg || 'Delete failed', 'error')
                }
              }}
              className="px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700"
            >
              Delete
            </button>
          </div>
        </ModalShell>
      )}
    </div>
  )
}

const CreateAssetModal: React.FC<{
  users: User[]
  onClose: () => void
  onSaved: () => void
  onError: (msg: string) => void
}> = ({ users, onClose, onSaved, onError }) => {
  const [form, setForm] = useState<CreateAssetRequest>({
    name: '',
    assetType: '',
    serialNumber: '',
    description: '',
    assignedTo: UNASSIGNED,
    location: '',
    purchaseDate: undefined,
    vendor: '',
    cost: undefined,
  })
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      const payload: CreateAssetRequest = {
        ...form,
        assignedTo: form.assignedTo === UNASSIGNED ? UNASSIGNED : form.assignedTo,
      }
      await assetAPI.create(payload)
      onSaved()
    } catch (err: unknown) {
      const msg =
        typeof err === 'object' && err !== null && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      onError(msg || 'Could not create asset')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title="Add asset" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <Field label="Name">
          <input
            required
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Type">
            <input
              required
              placeholder="Laptop, Monitor…"
              value={form.assetType}
              onChange={(e) => setForm({ ...form, assetType: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Serial number">
            <input
              required
              value={form.serialNumber}
              onChange={(e) => setForm({ ...form, serialNumber: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg font-mono text-sm"
            />
          </Field>
        </div>
        <Field label="Description">
          <textarea
            value={form.description || ''}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={2}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <Field label="Location">
          <input
            value={form.location || ''}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Vendor">
            <input
              value={form.vendor || ''}
              onChange={(e) => setForm({ ...form, vendor: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Cost">
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.cost ?? ''}
              onChange={(e) =>
                setForm({
                  ...form,
                  cost: e.target.value === '' ? undefined : Number(e.target.value),
                })
              }
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <Field label="Purchase date">
          <input
            type="datetime-local"
            value={form.purchaseDate ?? ''}
            onChange={(e) => setForm({ ...form, purchaseDate: e.target.value || undefined })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <Field label="Assign to user (optional)">
          <select
            value={form.assignedTo === UNASSIGNED ? String(UNASSIGNED) : String(form.assignedTo)}
            onChange={(e) => setForm({ ...form, assignedTo: Number(e.target.value) })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          >
            <option value={String(UNASSIGNED)}>Unassigned</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.firstName} {u.lastName} ({u.email})
              </option>
            ))}
          </select>
        </Field>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Saving…' : 'Create'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

const EditAssetModal: React.FC<{
  asset: Asset
  users: User[]
  onClose: () => void
  onSaved: () => void
  onError: (msg: string) => void
}> = ({ asset, users, onClose, onSaved, onError }) => {
  const assigned = asset.assignedTo ?? UNASSIGNED
  const [form, setForm] = useState<UpdateAssetRequest & { assignedTo: number }>({
    name: asset.name,
    assetType: asset.assetType,
    description: asset.description,
    location: asset.location,
    vendor: asset.vendor,
    cost: asset.cost,
    status: asset.status,
    assignedTo: assigned,
  })
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    try {
      const body: UpdateAssetRequest = {
        name: form.name,
        assetType: form.assetType,
        description: form.description,
        location: form.location,
        vendor: form.vendor,
        cost: form.cost,
        status: form.status,
        assignedTo: form.assignedTo,
      }
      await assetAPI.update(asset.id, body)
      onSaved()
    } catch (err: unknown) {
      const msg =
        typeof err === 'object' && err !== null && 'response' in err
          ? (err as { response?: { data?: { message?: string } } }).response?.data?.message
          : undefined
      onError(msg || 'Could not update asset')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <ModalShell title={`Edit — ${asset.name}`} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <Field label="Name">
          <input
            required
            value={form.name || ''}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Type">
            <input
              required
              value={form.assetType || ''}
              onChange={(e) => setForm({ ...form, assetType: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Status">
            <select
              value={
                STATUS_OPTIONS.includes(form.status as (typeof STATUS_OPTIONS)[number])
                  ? form.status!
                  : asset.status
              }
              onChange={(e) => setForm({ ...form, status: e.target.value as AssetStatus })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            >
              {STATUS_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Description">
          <textarea
            value={form.description || ''}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
            rows={2}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <Field label="Location">
          <input
            value={form.location || ''}
            onChange={(e) => setForm({ ...form, location: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </Field>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Vendor">
            <input
              value={form.vendor || ''}
              onChange={(e) => setForm({ ...form, vendor: e.target.value })}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
          <Field label="Cost">
            <input
              type="number"
              min={0}
              step="0.01"
              value={form.cost ?? ''}
              onChange={(e) =>
                setForm({
                  ...form,
                  cost: e.target.value === '' ? undefined : Number(e.target.value),
                })
              }
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </Field>
        </div>
        <Field label="Assign to user">
          <select
            value={form.assignedTo === UNASSIGNED ? String(UNASSIGNED) : String(form.assignedTo)}
            onChange={(e) => setForm({ ...form, assignedTo: Number(e.target.value) })}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          >
            <option value={String(UNASSIGNED)}>Unassigned</option>
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.firstName} {u.lastName} ({u.email})
              </option>
            ))}
          </select>
        </Field>
        <div className="flex justify-end gap-2 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-lg text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={submitting}
            className="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
          >
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

export default Assets
