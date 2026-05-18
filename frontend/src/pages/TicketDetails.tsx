import React, { useEffect, useMemo, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Lock, RotateCcw, Send, Pencil, Link2, UserPlus, X } from 'lucide-react'
import Avatar from '../components/Common/Avatar'
import { ticketAPI } from '../api/tickets'
import { userAPI } from '../api/users'
import { assetAPI } from '../api/assets'
import { Asset, Attachment, Comment, Ticket, TicketPriority, TicketStatus, User } from '../types'
import { Paperclip, Download, Trash2 as Trash } from 'lucide-react'
import { useNotification } from '../context/NotificationContext'
import { useAuth } from '../context/AuthContext'
import { priorityClass, statusClass } from './Tickets'

/**
 * Ticket details — role-aware controls.
 *
 *   USER (ticket creator):
 *     - sees public replies only (server filters internal notes)
 *     - can add a public reply
 *     - can reopen a RESOLVED/CLOSED ticket of their own
 *
 *   TECHNICIAN / ADMIN:
 *     - sees public replies AND internal notes (server returns both)
 *     - can add public replies, internal notes
 *     - can change status, priority
 *     - ADMIN can also assign/unassign
 */

const STATUSES: TicketStatus[] = [
  'NEW',
  'OPEN',
  'ASSIGNED',
  'IN_PROGRESS',
  'WAITING_FOR_USER',
  'WAITING_FOR_VENDOR',
  'ON_HOLD',
  'RESOLVED',
  'CLOSED',
  'REOPENED',
  'CANCELLED',
]
const PRIORITIES: TicketPriority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const TicketDetails: React.FC = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const { showToast } = useNotification()
  const { user } = useAuth()

  const [ticket, setTicket] = useState<Ticket | null>(null)
  const [comments, setComments] = useState<Comment[]>([])
  const [attachments, setAttachments] = useState<Attachment[]>([])
  const [attachmentBusy, setAttachmentBusy] = useState(false)
  const attachmentInputRef = React.useRef<HTMLInputElement>(null)
  const [loading, setLoading] = useState(true)

  const [reply, setReply] = useState('')
  const [internalNote, setInternalNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [assignOpen, setAssignOpen] = useState(false)
  const [linkAssetOpen, setLinkAssetOpen] = useState(false)
  const [editOpen, setEditOpen] = useState(false)
  /** Filled from ticket DTO enrichment and/or user/asset API fallbacks */
  const [creatorName, setCreatorName] = useState('')
  const [creatorEmail, setCreatorEmail] = useState('')
  const [creatorDept, setCreatorDept] = useState('')
  const [assignedLabel, setAssignedLabel] = useState<string>('')
  const [assetLabel, setAssetLabel] = useState<string>('')

  const isStaff = user?.role === 'ADMIN' || user?.role === 'TECHNICIAN'
  const isAdmin = user?.role === 'ADMIN'
  const isOwner = useMemo(
    () => !!user && !!ticket && user.id === ticket.createdBy,
    [user, ticket]
  )
  const canReopen =
    !!ticket &&
    (isStaff || isOwner) &&
    ticket.status !== 'CANCELLED' &&
    (ticket.status === 'RESOLVED' || ticket.status === 'CLOSED')

  const canCancel =
    !!ticket &&
    (isOwner || isAdmin) &&
    ticket.status !== 'RESOLVED' &&
    ticket.status !== 'CLOSED' &&
    ticket.status !== 'CANCELLED'

  /**
   * Permanent delete: admin in any state; technician only on tickets
   * assigned to them in a terminal state (RESOLVED/CLOSED/CANCELLED).
   * Backend enforces the same rule — the UI just hides the button for
   * everyone else to avoid the dead click.
   */
  const canDelete =
    !!ticket &&
    (isAdmin ||
      (user?.role === 'TECHNICIAN' &&
        ticket.assignedTo === user.id &&
        (ticket.status === 'RESOLVED' ||
          ticket.status === 'CLOSED' ||
          ticket.status === 'CANCELLED')))

  /** Who can open the full edit form (title/description/category/priority). */
  const canEdit =
    !!ticket &&
    ticket.status !== 'CANCELLED' &&
    (isAdmin ||
      (user?.role === 'TECHNICIAN' && ticket.assignedTo === user.id) ||
      (isOwner &&
        ticket.status !== 'RESOLVED' &&
        ticket.status !== 'CLOSED'))

  const numId = id ? Number(id) : NaN

  const loadAll = async () => {
    if (!Number.isFinite(numId)) return
    setLoading(true)
    try {
      const [tRes, cRes, aRes] = await Promise.all([
        ticketAPI.getById(numId),
        ticketAPI.getComments(numId),
        ticketAPI.listAttachments(numId).catch(() => ({ data: [] as Attachment[] })),
      ])
      setTicket(tRes.data)
      setComments(cRes.data)
      setAttachments(aRes.data || [])
    } catch {
      showToast('Could not load ticket', 'error')
    } finally {
      setLoading(false)
    }
  }

  const canUploadAttachments =
    !!ticket &&
    ticket.status !== 'CANCELLED' &&
    (isAdmin ||
      isOwner ||
      (user?.role === 'TECHNICIAN' && ticket.assignedTo === user.id))

  const onAttachmentSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (!ticket) return
    const files = e.target.files
    if (!files || files.length === 0) return
    setAttachmentBusy(true)
    let failed = 0
    for (const f of Array.from(files)) {
      try {
        await ticketAPI.uploadAttachment(ticket.id, f)
      } catch {
        failed++
      }
    }
    try {
      const r = await ticketAPI.listAttachments(ticket.id)
      setAttachments(r.data || [])
    } catch {
      /* ignore */
    }
    setAttachmentBusy(false)
    if (attachmentInputRef.current) attachmentInputRef.current.value = ''
    if (failed > 0) showToast(`${failed} attachment(s) failed to upload`, 'warning')
    else showToast('Attachment uploaded', 'success')
  }

  const onDeleteAttachment = async (a: Attachment) => {
    if (!window.confirm(`Delete "${a.originalFilename}"?`)) return
    try {
      await ticketAPI.deleteAttachment(a.id)
      setAttachments((prev) => prev.filter((x) => x.id !== a.id))
      showToast('Attachment removed', 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not delete attachment', 'error')
    }
  }

  useEffect(() => {
    loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(() => {
    if (!ticket) {
      setCreatorName('')
      setCreatorEmail('')
      setCreatorDept('')
      setAssignedLabel('')
      setAssetLabel('')
      return
    }

    setCreatorName(ticket.createdByName || (ticket.createdBy != null ? `User #${ticket.createdBy}` : '—'))
    setCreatorEmail(ticket.createdByEmail || '')
    setCreatorDept(ticket.createdByDepartment || '')

    if (ticket.assignedTo == null) {
      setAssignedLabel('Unassigned')
    } else {
      setAssignedLabel(
        [ticket.assignedToName, ticket.assignedToEmail].filter(Boolean).join(' — ') ||
          `User #${ticket.assignedTo}`
      )
    }

    if (!ticket.linkedAssetId) {
      setAssetLabel('None')
    } else {
      setAssetLabel(ticket.linkedAssetLabel || `…`)
    }

    let cancelled = false
    ;(async () => {
      try {
        if (!ticket.createdByName) {
          const c = await userAPI.getById(ticket.createdBy)
          if (!cancelled) {
            const u: User = c.data
            setCreatorName(`${u.firstName} ${u.lastName}`.trim())
            setCreatorEmail(u.email)
            setCreatorDept(u.department || '')
          }
        }
        if (ticket.assignedTo && !ticket.assignedToName) {
          const a = await userAPI.getById(ticket.assignedTo)
          if (!cancelled) {
            const u: User = a.data
            setAssignedLabel(`${u.firstName} ${u.lastName} — ${u.email}`)
          }
        }
        if (ticket.linkedAssetId && !ticket.linkedAssetLabel) {
          const ar = await assetAPI.getById(ticket.linkedAssetId)
          if (!cancelled) {
            const as: Asset = ar.data
            setAssetLabel(`${as.name} — ${as.serialNumber || 'no serial'} (${as.status})`)
          }
        }
      } catch {
        if (!cancelled) {
          if (!ticket.createdByName) {
            setCreatorName(`User #${ticket.createdBy}`)
            setCreatorEmail('')
            setCreatorDept('')
          }
          if (ticket.assignedTo && !ticket.assignedToName) {
            setAssignedLabel(`User #${ticket.assignedTo}`)
          }
          if (ticket.linkedAssetId && !ticket.linkedAssetLabel) {
            setAssetLabel(`Asset #${ticket.linkedAssetId}`)
          }
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [ticket])

  const submitPublicReply = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!reply.trim() || !ticket) return
    setBusy(true)
    try {
      await ticketAPI.addComment(ticket.id, { commentText: reply })
      setReply('')
      const c = await ticketAPI.getComments(ticket.id)
      setComments(c.data)
      showToast('Reply posted', 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not post reply', 'error')
    } finally {
      setBusy(false)
    }
  }

  const submitInternalNote = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!internalNote.trim() || !ticket) return
    setBusy(true)
    try {
      await ticketAPI.addInternalNote(ticket.id, { commentText: internalNote })
      setInternalNote('')
      const c = await ticketAPI.getComments(ticket.id)
      setComments(c.data)
      showToast('Internal note added', 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not add note', 'error')
    } finally {
      setBusy(false)
    }
  }

  const onChangeStatus = async (next: TicketStatus) => {
    if (!ticket || next === ticket.status) return
    try {
      const r = await ticketAPI.changeStatus(ticket.id, next)
      setTicket(r.data)
      showToast(`Status → ${next.replace(/_/g, ' ')}`, 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not change status', 'error')
    }
  }

  const onChangePriority = async (next: TicketPriority) => {
    if (!ticket || next === ticket.priority) return
    try {
      const r = await ticketAPI.changePriority(ticket.id, next)
      setTicket(r.data)
      showToast(`Priority → ${next}`, 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not change priority', 'error')
    }
  }

  const submitAssign = async (newAssigneeId: number | null) => {
    if (!ticket) return
    try {
      const r = await ticketAPI.assign(ticket.id, newAssigneeId)
      setTicket(r.data)
      showToast(newAssigneeId === null ? 'Unassigned' : 'Ticket assigned', 'success')
      setAssignOpen(false)
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not assign', 'error')
    }
  }

  const submitLinkAsset = async (assetId: number | null) => {
    if (!ticket) return
    try {
      const r = await ticketAPI.linkAsset(ticket.id, assetId)
      setTicket(r.data)
      showToast(assetId === null ? 'Asset unlinked' : 'Asset linked', 'success')
      setLinkAssetOpen(false)
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not update linked asset', 'error')
    }
  }

  const submitEdit = async (patch: Partial<Ticket>) => {
    if (!ticket) return
    try {
      const r = await ticketAPI.update(ticket.id, patch as any)
      setTicket(r.data)
      showToast('Ticket updated', 'success')
      setEditOpen(false)
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not update ticket', 'error')
    }
  }

  const onReopen = async () => {
    if (!ticket) return
    try {
      const r = await ticketAPI.reopen(ticket.id)
      setTicket(r.data)
      showToast('Ticket reopened', 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not reopen', 'error')
    }
  }

  const onDelete = async () => {
    if (!ticket) return
    const label = ticket.ticketNumber || `#${ticket.id}`
    if (!window.confirm(
      `Permanently delete ticket ${label}?\n\nThis removes the ticket, all replies/notes, and all attachments. It cannot be undone.`
    )) {
      return
    }
    setBusy(true)
    try {
      await ticketAPI.delete(ticket.id)
      showToast(`Ticket ${label} deleted`, 'success')
      navigate('/tickets')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not delete ticket', 'error')
    } finally {
      setBusy(false)
    }
  }

  const onCancel = async () => {
    if (!ticket) return
    const ok = window.confirm('Cancel this ticket? It will be marked as cancelled.')
    if (!ok) return
    setBusy(true)
    try {
      const r = await ticketAPI.cancel(ticket.id)
      setTicket(r.data)
      showToast('Ticket cancelled', 'success')
    } catch (e: any) {
      showToast(e.response?.data?.message || 'Could not cancel ticket', 'error')
    } finally {
      setBusy(false)
    }
  }

  if (loading || !ticket) {
    return (
      <div className="max-w-4xl p-6">
        <button
          onClick={() => navigate('/tickets')}
          className="text-primary-600 hover:text-primary-700 mb-4 font-medium"
        >
          ← Back to Tickets
        </button>
        <p className="text-gray-600">{loading ? 'Loading…' : 'Ticket not found.'}</p>
      </div>
    )
  }

  return (
    <div className="max-w-5xl">
      <button
        onClick={() => navigate('/tickets')}
        className="text-primary-600 hover:text-primary-700 mb-4 font-medium"
      >
        ← Back to Tickets
      </button>

      <div className="grid grid-cols-3 gap-6">
        {/* Main column */}
        <div className="col-span-2 space-y-6">
          <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-start justify-between mb-2 gap-3">
              <h1 className="text-2xl font-bold text-gray-900 flex-1 min-w-0">{ticket.title}</h1>
              <div className="flex items-center gap-2 shrink-0">
                {canEdit && (
                  <button
                    type="button"
                    onClick={() => setEditOpen(true)}
                    className="flex items-center gap-1 px-2 py-1 text-sm rounded-md border border-gray-300 text-gray-700 hover:bg-gray-50"
                  >
                    <Pencil className="h-3.5 w-3.5" /> Edit
                  </button>
                )}
                <span className="font-mono text-sm text-gray-500">
                  {ticket.ticketNumber || `#${ticket.id}`}
                </span>
              </div>
            </div>
            <div className="flex flex-wrap gap-2 mb-4">
              <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusClass(ticket.status)}`}>
                {ticket.status.replace(/_/g, ' ')}
              </span>
              <span className={`px-2 py-1 rounded-full text-xs font-medium ${priorityClass(ticket.priority)}`}>
                {ticket.priority}
              </span>
              <span className="px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-700">
                {ticket.category}
              </span>
            </div>
            <p className="text-gray-800 whitespace-pre-wrap">{ticket.description}</p>
          </div>

          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-4">Conversation</h2>
            {comments.length === 0 ? (
              <p className="text-gray-600 text-sm">No replies yet.</p>
            ) : (
              <div className="space-y-3">
                {comments.map((c) => {
                  const [first, ...rest] = (c.authorName || '').split(' ')
                  const last = rest.join(' ')
                  return (
                    <div
                      key={c.id}
                      className={`rounded-lg p-4 flex gap-3 ${
                        c.isInternal
                          ? 'bg-amber-50 border border-amber-200'
                          : 'bg-gray-50 border border-gray-100'
                      }`}
                    >
                      <Avatar
                        firstName={first || undefined}
                        lastName={last || undefined}
                        imageUrl={c.authorAvatarUrl}
                        size={36}
                      />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between mb-1">
                          <p className="font-medium text-gray-900 text-sm">
                            {c.authorName || `User #${c.userId}`}
                            {c.authorRole ? (
                              <span className="text-gray-500 font-normal"> · {c.authorRole}</span>
                            ) : null}
                          </p>
                          {c.isInternal && (
                            <span className="flex items-center gap-1 text-xs font-medium text-amber-800">
                              <Lock className="h-3 w-3" />
                              Internal note
                            </span>
                          )}
                        </div>
                        <p className="text-xs text-gray-500 mb-2">{fmtDt(c.createdAt)}</p>
                        <p className="text-gray-800 whitespace-pre-wrap text-sm">{c.commentText}</p>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}

            {/* Public reply box — not for cancelled tickets */}
            {ticket.status !== 'CANCELLED' && (
            <form onSubmit={submitPublicReply} className="mt-6 space-y-2">
              <label className="block text-sm font-medium text-gray-700">
                {isStaff ? 'Reply to user (public)' : 'Add a reply'}
              </label>
              <textarea
                value={reply}
                onChange={(e) => setReply(e.target.value)}
                rows={3}
                placeholder="Type your message…"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              />
              <div className="flex justify-end">
                <button
                  type="submit"
                  disabled={busy || !reply.trim()}
                  className="flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 disabled:opacity-50"
                >
                  <Send className="h-4 w-4" />
                  {busy ? 'Sending…' : 'Post reply'}
                </button>
              </div>
            </form>
            )}

            {/* Attachments */}
            <div className="mt-6 pt-6 border-t border-gray-100">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-gray-800 flex items-center gap-2">
                  <Paperclip className="h-4 w-4" />
                  Attachments ({attachments.length})
                </h3>
                {canUploadAttachments && (
                  <div>
                    <button
                      type="button"
                      onClick={() => attachmentInputRef.current?.click()}
                      disabled={attachmentBusy}
                      className="text-sm text-primary-600 hover:underline disabled:opacity-50"
                    >
                      {attachmentBusy ? 'Uploading…' : '+ Add file'}
                    </button>
                    <input
                      ref={attachmentInputRef}
                      type="file"
                      multiple
                      accept="image/jpeg,image/jpg,image/png,image/gif,image/webp,application/pdf"
                      className="hidden"
                      onChange={onAttachmentSelected}
                    />
                  </div>
                )}
              </div>
              {attachments.length === 0 ? (
                <p className="text-xs text-gray-500">No attachments.</p>
              ) : (
                <ul className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                  {attachments.map((a) => {
                    const isImage = a.contentType?.startsWith('image/')
                    const canDelete =
                      isAdmin || (user?.id != null && user.id === a.uploadedBy)
                    return (
                      <li
                        key={a.id}
                        className="relative border border-gray-200 rounded-lg overflow-hidden bg-gray-50 group"
                      >
                        {isImage ? (
                          <a href={a.downloadUrl} target="_blank" rel="noreferrer">
                            <img
                              src={a.previewUrl}
                              alt={a.originalFilename}
                              className="w-full h-28 object-cover"
                            />
                          </a>
                        ) : (
                          <a
                            href={a.downloadUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="flex items-center justify-center w-full h-28 text-xs text-gray-600 px-2 text-center hover:bg-gray-100"
                          >
                            {a.originalFilename}
                          </a>
                        )}
                        <div className="px-2 py-1 text-[10px] text-gray-600 flex items-center justify-between">
                          <span className="truncate" title={a.originalFilename}>
                            {a.originalFilename}
                          </span>
                          <a
                            href={a.downloadUrl}
                            download={a.originalFilename}
                            className="text-primary-600 hover:underline shrink-0 ml-1"
                            title="Download"
                          >
                            <Download className="h-3 w-3" />
                          </a>
                        </div>
                        {canDelete && (
                          <button
                            type="button"
                            onClick={() => void onDeleteAttachment(a)}
                            className="absolute top-1 right-1 bg-white/90 hover:bg-red-50 text-red-600 rounded-full p-1 shadow opacity-0 group-hover:opacity-100 transition"
                            title="Remove"
                          >
                            <Trash className="h-3 w-3" />
                          </button>
                        )}
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>

            {/* Internal note box — staff only */}
            {isStaff && ticket.status !== 'CANCELLED' && (
              <form
                onSubmit={submitInternalNote}
                className="mt-4 p-4 bg-amber-50 border border-amber-200 rounded-lg space-y-2"
              >
                <label className="flex items-center gap-2 text-sm font-medium text-amber-900">
                  <Lock className="h-4 w-4" />
                  Internal note (not visible to the user)
                </label>
                <textarea
                  value={internalNote}
                  onChange={(e) => setInternalNote(e.target.value)}
                  rows={2}
                  placeholder="Note for other staff…"
                  className="w-full px-3 py-2 border border-amber-200 rounded-lg focus:ring-2 focus:ring-amber-400 bg-white"
                />
                <div className="flex justify-end">
                  <button
                    type="submit"
                    disabled={busy || !internalNote.trim()}
                    className="flex items-center gap-2 px-4 py-2 bg-amber-600 text-white rounded-lg hover:bg-amber-700 disabled:opacity-50"
                  >
                    <Lock className="h-4 w-4" />
                    Add internal note
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>

        {/* Side column */}
        <div className="space-y-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="font-bold text-gray-900 mb-4">Details</h3>
            <div className="space-y-4 text-sm">
              <Field label="Created by" value={creatorName || '—'} />
              <Field
                label="Email"
                value={
                  creatorEmail ? (
                    <a href={`mailto:${creatorEmail}`} className="text-primary-600 hover:underline">
                      {creatorEmail}
                    </a>
                  ) : (
                    '—'
                  )
                }
              />
              <Field label="Department" value={creatorDept || '—'} />
              <Field label="Assigned to" value={assignedLabel || '…'} />
              <Field label="Linked asset" value={assetLabel || '…'} />
              {(ticket.building || ticket.locationDepartment || ticket.roomNumber || ticket.locationNotes) && (
                <Field
                  label="Device location"
                  value={
                    <span className="block whitespace-pre-line">
                      {[
                        ticket.building && `Building: ${ticket.building}`,
                        ticket.locationDepartment && `Dept: ${ticket.locationDepartment}`,
                        ticket.roomNumber && `Room: ${ticket.roomNumber}`,
                        ticket.locationNotes && ticket.locationNotes,
                      ]
                        .filter(Boolean)
                        .join('\n')}
                    </span>
                  }
                />
              )}
              <Field label="Created" value={fmtDt(ticket.createdAt)} />
              <Field label="Updated" value={fmtDt(ticket.updatedAt)} />
              {ticket.resolvedAt && <Field label="Resolved" value={fmtDt(ticket.resolvedAt)} />}
              {ticket.closedAt && <Field label="Closed" value={fmtDt(ticket.closedAt)} />}
            </div>
          </div>

          {/* Staff controls */}
          {isStaff && ticket.status !== 'CANCELLED' && (
            <div className="bg-white rounded-lg shadow p-6 space-y-4">
              <h3 className="font-bold text-gray-900">Workflow</h3>
              <div>
                <label className="block text-sm text-gray-600 mb-1">Status</label>
                <select
                  value={ticket.status}
                  onChange={(e) => onChangeStatus(e.target.value as TicketStatus)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  {STATUSES.map((s) => (
                    <option key={s} value={s}>
                      {s.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-600 mb-1">Priority</label>
                <select
                  value={ticket.priority}
                  onChange={(e) => onChangePriority(e.target.value as TicketPriority)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                >
                  {PRIORITIES.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </div>
              {isAdmin && (
                <>
                  <button
                    onClick={() => setAssignOpen(true)}
                    className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
                  >
                    <UserPlus className="h-4 w-4" />
                    {ticket.assignedTo ? 'Reassign technician' : 'Assign technician'}
                  </button>
                  <button
                    onClick={() => setLinkAssetOpen(true)}
                    className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-primary-600 text-primary-700 rounded-lg hover:bg-primary-50"
                  >
                    <Link2 className="h-4 w-4" />
                    {ticket.linkedAssetId ? 'Change linked asset' : 'Link an asset'}
                  </button>
                </>
              )}
            </div>
          )}

          {/* Cancel — creator or admin, until resolved/closed */}
          {canCancel && (
            <button
              type="button"
              onClick={() => void onCancel()}
              disabled={busy}
              className="w-full flex items-center justify-center gap-2 px-4 py-2 border border-gray-300 text-gray-800 rounded-lg hover:bg-gray-50 disabled:opacity-50"
            >
              Cancel ticket
            </button>
          )}

          {/* Reopen — for owner or staff when status is RESOLVED/CLOSED */}
          {canReopen && (
            <button
              onClick={onReopen}
              className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-rose-600 text-white rounded-lg hover:bg-rose-700"
            >
              <RotateCcw className="h-4 w-4" />
              Reopen ticket
            </button>
          )}

          {/* Permanent delete — admin (any state) or technician (their own,
              terminal-state tickets). Destructive, so it sits at the very
              bottom and uses red. */}
          {canDelete && (
            <button
              type="button"
              onClick={() => void onDelete()}
              disabled={busy}
              className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
              title="Delete this ticket and all its replies and attachments permanently"
            >
              <Trash className="h-4 w-4" />
              Delete permanently
            </button>
          )}
        </div>
      </div>

      {assignOpen && (
        <AssignTechnicianModal
          currentAssigneeId={ticket.assignedTo ?? null}
          currentAssigneeLabel={assignedLabel}
          onClose={() => setAssignOpen(false)}
          onSubmit={submitAssign}
        />
      )}
      {linkAssetOpen && (
        <LinkAssetModal
          currentAssetId={ticket.linkedAssetId ?? null}
          onClose={() => setLinkAssetOpen(false)}
          onSubmit={submitLinkAsset}
        />
      )}
      {editOpen && (
        <EditTicketModal
          ticket={ticket}
          canEditStatusAndPriority={isStaff}
          onClose={() => setEditOpen(false)}
          onSubmit={submitEdit}
        />
      )}
    </div>
  )
}

const Field: React.FC<{ label: string; value: React.ReactNode }> = ({ label, value }) => (
  <div>
    <p className="text-gray-500 text-xs uppercase tracking-wide">{label}</p>
    <p className="font-medium text-gray-900">{value}</p>
  </div>
)

function fmtDt(s?: string) {
  if (!s) return '—'
  try {
    return new Date(s).toLocaleString()
  } catch {
    return s
  }
}

// ────────────────────────────────────────────────────────────────────────────
//  Modals
// ────────────────────────────────────────────────────────────────────────────

const ModalShell: React.FC<{
  title: string
  onClose: () => void
  children: React.ReactNode
  wide?: boolean
}> = ({ title, onClose, children, wide }) => (
  <div className="fixed inset-0 bg-black/40 flex items-center justify-center p-4 z-50">
    <div className={`bg-white rounded-lg shadow-xl w-full ${wide ? 'max-w-2xl' : 'max-w-md'} p-6`}>
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-lg font-bold text-gray-900">{title}</h2>
        <button onClick={onClose} className="p-1 rounded hover:bg-gray-100">
          <X className="h-4 w-4 text-gray-500" />
        </button>
      </div>
      {children}
    </div>
  </div>
)

const AssignTechnicianModal: React.FC<{
  currentAssigneeId: number | null
  currentAssigneeLabel: string
  onClose: () => void
  onSubmit: (id: number | null) => Promise<void> | void
}> = ({ currentAssigneeId, currentAssigneeLabel, onClose, onSubmit }) => {
  const [techs, setTechs] = useState<User[]>([])
  const [admins, setAdmins] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string>(
    currentAssigneeId == null ? '' : String(currentAssigneeId)
  )
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([
      userAPI.getTechnicians().catch(() => ({ data: [] as User[] })),
      // Admins can also be assignees (small teams).
      userAPI.getAll({ }).then((r) => ({ data: r.data.filter((u) => u.role === 'ADMIN') })).catch(() => ({ data: [] as User[] })),
    ])
      .then(([t, a]) => {
        if (cancelled) return
        setTechs(t.data || [])
        setAdmins(a.data || [])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    const next = selectedId === '' ? null : Number(selectedId)
    await onSubmit(next)
    setSubmitting(false)
  }

  return (
    <ModalShell title="Assign technician" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        {currentAssigneeId != null && (
          <p className="text-sm text-gray-600">
            Currently assigned to <strong>{currentAssigneeLabel}</strong>.
          </p>
        )}
        <label className="block">
          <span className="text-sm font-medium text-gray-700 mb-1 block">Assignee</span>
          <select
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          >
            <option value="">— Unassigned —</option>
            {techs.length > 0 && (
              <optgroup label="Technicians">
                {techs
                  .filter((u) => u.status === 'ACTIVE')
                  .map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.firstName} {u.lastName} ({u.email})
                    </option>
                  ))}
              </optgroup>
            )}
            {admins.length > 0 && (
              <optgroup label="Administrators">
                {admins
                  .filter((u) => u.status === 'ACTIVE')
                  .map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.firstName} {u.lastName} ({u.email})
                    </option>
                  ))}
              </optgroup>
            )}
          </select>
          {loading && <p className="text-xs text-gray-500 mt-1">Loading users…</p>}
          {!loading && techs.length === 0 && admins.length === 0 && (
            <p className="text-xs text-red-600 mt-1">No technicians found. Create one in Manage Users.</p>
          )}
        </label>
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

const LinkAssetModal: React.FC<{
  currentAssetId: number | null
  onClose: () => void
  onSubmit: (id: number | null) => Promise<void> | void
}> = ({ currentAssetId, onClose, onSubmit }) => {
  const [all, setAll] = useState<Asset[]>([])
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(true)
  const [selectedId, setSelectedId] = useState<string>(
    currentAssetId == null ? '' : String(currentAssetId)
  )
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    assetAPI
      .getAll()
      .then((r) => {
        if (!cancelled) setAll(r.data || [])
      })
      .catch(() => {
        if (!cancelled) setAll([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const matches = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return all.slice(0, 100)
    return all
      .filter(
        (a) =>
          a.name.toLowerCase().includes(q) ||
          (a.serialNumber || '').toLowerCase().includes(q) ||
          (a.assetType || '').toLowerCase().includes(q)
      )
      .slice(0, 100)
  }, [all, filter])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    const next = selectedId === '' ? null : Number(selectedId)
    await onSubmit(next)
    setSubmitting(false)
  }

  return (
    <ModalShell title="Link asset" onClose={onClose} wide>
      <form onSubmit={submit} className="space-y-3">
        <input
          type="text"
          placeholder="Filter by name, serial, or type…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg"
        />
        <div className="max-h-64 overflow-y-auto border border-gray-200 rounded-lg divide-y">
          <label className="flex items-center gap-2 px-3 py-2 hover:bg-gray-50">
            <input
              type="radio"
              name="asset"
              value=""
              checked={selectedId === ''}
              onChange={() => setSelectedId('')}
            />
            <span className="text-sm text-gray-700">— None (unlink) —</span>
          </label>
          {loading && <p className="px-3 py-2 text-sm text-gray-500">Loading…</p>}
          {!loading &&
            matches.map((a) => (
              <label
                key={a.id}
                className="flex items-start gap-2 px-3 py-2 hover:bg-gray-50 cursor-pointer"
              >
                <input
                  type="radio"
                  name="asset"
                  value={a.id}
                  checked={selectedId === String(a.id)}
                  onChange={() => setSelectedId(String(a.id))}
                  className="mt-1"
                />
                <span className="text-sm">
                  <span className="block font-medium text-gray-900">{a.name}</span>
                  <span className="block text-xs text-gray-600">
                    {a.assetType}
                    {a.serialNumber ? ` · SN ${a.serialNumber}` : ''}
                    {` · ${a.status}`}
                    {a.assignedTo ? ` · assigned to user #${a.assignedTo}` : ''}
                  </span>
                </span>
              </label>
            ))}
          {!loading && matches.length === 0 && (
            <p className="px-3 py-2 text-sm text-gray-500">No matching assets.</p>
          )}
        </div>
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

const EditTicketModal: React.FC<{
  ticket: Ticket
  canEditStatusAndPriority: boolean
  onClose: () => void
  onSubmit: (patch: Partial<Ticket>) => Promise<void> | void
}> = ({ ticket, canEditStatusAndPriority, onClose, onSubmit }) => {
  const [title, setTitle] = useState(ticket.title)
  const [description, setDescription] = useState(ticket.description || '')
  const [category, setCategory] = useState(ticket.category || '')
  const [priority, setPriority] = useState<TicketPriority>(ticket.priority)
  const [status, setStatus] = useState<TicketStatus>(ticket.status)
  const [building, setBuilding] = useState(ticket.building || '')
  const [locationDepartment, setLocationDepartment] = useState(ticket.locationDepartment || '')
  const [roomNumber, setRoomNumber] = useState(ticket.roomNumber || '')
  const [locationNotes, setLocationNotes] = useState(ticket.locationNotes || '')
  const [submitting, setSubmitting] = useState(false)

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setSubmitting(true)
    const patch: Partial<Ticket> = {}
    if (title !== ticket.title) patch.title = title
    if (description !== (ticket.description || '')) patch.description = description
    if (category !== (ticket.category || '')) patch.category = category
    if (priority !== ticket.priority) patch.priority = priority
    if (canEditStatusAndPriority && status !== ticket.status) patch.status = status
    if (building !== (ticket.building || '')) patch.building = building
    if (locationDepartment !== (ticket.locationDepartment || ''))
      patch.locationDepartment = locationDepartment
    if (roomNumber !== (ticket.roomNumber || '')) patch.roomNumber = roomNumber
    if (locationNotes !== (ticket.locationNotes || '')) patch.locationNotes = locationNotes
    await onSubmit(patch)
    setSubmitting(false)
  }

  return (
    <ModalShell title="Edit ticket" onClose={onClose} wide>
      <form onSubmit={submit} className="space-y-3">
        <label className="block">
          <span className="text-sm font-medium text-gray-700 mb-1 block">Title</span>
          <input
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </label>
        <label className="block">
          <span className="text-sm font-medium text-gray-700 mb-1 block">Description</span>
          <textarea
            required
            rows={5}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="w-full px-3 py-2 border border-gray-300 rounded-lg"
          />
        </label>
        <div className="grid grid-cols-3 gap-3">
          <label className="block">
            <span className="text-sm font-medium text-gray-700 mb-1 block">Category</span>
            <input
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            />
          </label>
          <label className="block">
            <span className="text-sm font-medium text-gray-700 mb-1 block">Priority</span>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as TicketPriority)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg"
            >
              {PRIORITIES.map((p) => (
                <option key={p} value={p}>
                  {p}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="text-sm font-medium text-gray-700 mb-1 block">Status</span>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as TicketStatus)}
              disabled={!canEditStatusAndPriority}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg disabled:bg-gray-50"
            >
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s.replace(/_/g, ' ')}
                </option>
              ))}
            </select>
            {!canEditStatusAndPriority && (
              <p className="text-xs text-gray-500 mt-1">Only staff can change status.</p>
            )}
          </label>
        </div>
        <fieldset className="border border-gray-200 rounded-lg p-3">
          <legend className="px-1 text-xs font-medium text-gray-600">
            Device location (optional)
          </legend>
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="text-xs text-gray-600 mb-1 block">Building</span>
              <input
                value={building}
                onChange={(e) => setBuilding(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              />
            </label>
            <label className="block">
              <span className="text-xs text-gray-600 mb-1 block">Department</span>
              <input
                value={locationDepartment}
                onChange={(e) => setLocationDepartment(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              />
            </label>
            <label className="block">
              <span className="text-xs text-gray-600 mb-1 block">Room / Office</span>
              <input
                value={roomNumber}
                onChange={(e) => setRoomNumber(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              />
            </label>
            <label className="block">
              <span className="text-xs text-gray-600 mb-1 block">Notes</span>
              <input
                value={locationNotes}
                onChange={(e) => setLocationNotes(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm"
              />
            </label>
          </div>
        </fieldset>
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
            {submitting ? 'Saving…' : 'Save changes'}
          </button>
        </div>
      </form>
    </ModalShell>
  )
}

export default TicketDetails
