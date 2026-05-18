import axios, { AxiosError, AxiosInstance, AxiosRequestConfig, InternalAxiosRequestConfig } from 'axios'

/**
 * Resolve the API base URL.
 *
 * Priority:
 *   1. Vite env (VITE_API_URL)             — set at build time / via .env
 *   2. /api on the current host             — production behind a reverse proxy
 *   3. http://localhost:8000/api            — local dev fallback
 *
 * In production the SPA is served by nginx behind the gateway, so calling
 * "/api" relative-path lets browsers hit the same origin and avoids CORS.
 */
function resolveBaseUrl(): string {
  const fromEnv = import.meta.env.VITE_API_URL as string | undefined
  if (fromEnv && fromEnv.trim().length > 0) {
    return fromEnv.trim()
  }

  if (typeof window !== 'undefined' && window.location && window.location.origin) {
    if (window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') {
      return `${window.location.origin}/api`
    }
  }
  return 'http://localhost:8000/api'
}

const API_BASE_URL = resolveBaseUrl()

const client: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  // Allow cookies if the gateway ever switches to cookie-based auth.
  withCredentials: false,
  timeout: 15000,
})

// --- Request interceptor: attach Bearer token -------------------------------
client.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers = config.headers ?? {}
      config.headers.Authorization = `Bearer ${token}`
    }
    // For FormData bodies, drop the default JSON Content-Type so the browser
    // sets the proper `multipart/form-data; boundary=…` header itself. If you
    // pass `multipart/form-data` without a boundary, the server can't parse
    // the body (this was breaking ticket attachment + avatar uploads).
    if (typeof FormData !== 'undefined' && config.data instanceof FormData) {
      if (config.headers) {
        delete (config.headers as Record<string, unknown>)['Content-Type']
        delete (config.headers as Record<string, unknown>)['content-type']
      }
    }
    return config
  },
  (error) => Promise.reject(error)
)

// --- Response interceptor: refresh-on-401 + redirect on hard failure --------
//
// Single-flight refresh: if many requests fire and 401 simultaneously, only the
// first one calls /api/auth/refresh; the rest queue until that promise settles.
let refreshing: Promise<string | null> | null = null

async function performRefresh(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken')
  if (!refreshToken) return null
  try {
    const base =
      API_BASE_URL.startsWith('/') && typeof window !== 'undefined'
        ? `${window.location.origin}${API_BASE_URL}`
        : API_BASE_URL
    const resp = await axios.post(
      `${base}/auth/refresh`,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } }
    )
    const newToken = resp.data?.token as string | undefined
    const newRefresh = resp.data?.refreshToken as string | undefined
    if (newToken) localStorage.setItem('token', newToken)
    if (newRefresh) localStorage.setItem('refreshToken', newRefresh)
    return newToken ?? null
  } catch {
    return null
  }
}

function clearAuthAndRedirect() {
  localStorage.removeItem('token')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('user')
  if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

client.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retry?: boolean }) | undefined

    if (error.response?.status === 401 && original && !original._retry) {
      original._retry = true

      if (!refreshing) refreshing = performRefresh()
      const newToken = await refreshing
      refreshing = null

      if (newToken) {
        original.headers = original.headers ?? {}
        ;(original.headers as Record<string, string>).Authorization = `Bearer ${newToken}`
        return client.request(original)
      }
      clearAuthAndRedirect()
    }

    return Promise.reject(error)
  }
)

export { API_BASE_URL }
export default client
