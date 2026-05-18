import axios from 'axios'

/**
 * Turns Axios/backend errors into a single user-visible string.
 */
export function getApiErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Record<string, unknown> | undefined

    if (data?.message && typeof data.message === 'string') {
      return data.message
    }

    const errs = data?.errors
    if (errs && typeof errs === 'object' && errs !== null) {
      const first = Object.values(errs)[0]
      if (typeof first === 'string') return first
    }

    const code = error.code
    if (code === 'ERR_NETWORK' || error.message === 'Network Error') {
      return 'Cannot reach the API. With Docker, run `docker compose up --build` so nginx can proxy /api to the gateway; otherwise ensure the gateway is listening on port 8000.'
    }

    if (error.response?.status) {
      return `Request failed (HTTP ${error.response.status})`
    }
  }

  if (error instanceof Error) return error.message
  return 'Something went wrong'
}
