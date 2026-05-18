import client from './client'
import { Asset } from '../types'

/** Maps to asset-service AssetDTO (fields: name, assetType, serialNumber, …). */
export interface CreateAssetRequest {
  name: string
  assetType: string
  serialNumber: string
  description?: string
  /** Use 0 when unassigned (backend requires a value). */
  assignedTo?: number
  location?: string
  purchaseDate?: string
  vendor?: string
  cost?: number
}

export interface UpdateAssetRequest {
  name?: string
  assetType?: string
  serialNumber?: string
  description?: string
  location?: string
  purchaseDate?: string
  vendor?: string
  cost?: number
  status?: import('../types').AssetStatus
  /** Use 0 for unassigned (matches asset_db non-null column). */
  assignedTo?: number
}

export const assetAPI = {
  getAll: (params?: Record<string, unknown>) =>
    client.get<Asset[]>('/assets', { params }),

  getById: (id: number) =>
    client.get<Asset>(`/assets/${id}`),

  create: (data: CreateAssetRequest) =>
    client.post<Asset>('/assets', data),

  update: (id: number, data: UpdateAssetRequest) =>
    client.put<Asset>(`/assets/${id}`, data),

  delete: (id: number) =>
    client.delete(`/assets/${id}`),

  /** Backend route: GET /api/assets/assigned-to/{userId} */
  getByAssignedTo: (userId: number) =>
    client.get<Asset[]>(`/assets/assigned-to/${userId}`),

  search: (query: string) =>
    client.get<Asset[]>('/assets/search', { params: { query } }),
}
