export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type RequestOptions = Omit<RequestInit, 'body' | 'headers'> & {
  body?: unknown
  headers?: Record<string, string>
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
let csrfInitialization: Promise<void> | undefined

function getCsrfToken(): string | undefined {
  const metaToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content')
  if (metaToken) return metaToken

  // The token is intentionally read from the CSRF cookie only. Authentication remains
  // in the HttpOnly server session cookie and is never exposed to this application.
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]
}

async function ensureCsrfToken(): Promise<void> {
  if (getCsrfToken()) return
  if (!csrfInitialization) {
    csrfInitialization = fetch(`${API_BASE_URL}/csrf`, { headers: { Accept: 'application/json' }, credentials: 'same-origin' })
      .then(async (response) => {
        if (!response.ok) {
          const payload = await response.json().catch(() => undefined) as { message?: string; code?: string } | undefined
          throw new ApiError(payload?.message ?? `CSRF 初始化失败（${response.status}）`, response.status, payload?.code)
        }
      })
      .finally(() => { csrfInitialization = undefined })
  }
  await csrfInitialization
}

/**
 * API boundary for the Spring Boot backend. It deliberately never reads or stores access tokens.
 * Once IAM SSO is enabled, the backend session cookie is sent only on same-origin requests.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith('/')) throw new Error('API path must start with /')

  const method = options.method?.toUpperCase() ?? 'GET'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) await ensureCsrfToken()
  const csrfToken = getCsrfToken()
  const headers = new Headers({ Accept: 'application/json', ...options.headers })
  if (options.body !== undefined) headers.set('Content-Type', 'application/json')
  if (csrfToken && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    headers.set('X-CSRF-TOKEN', decodeURIComponent(csrfToken))
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: 'same-origin',
  })

  if (!response.ok) {
    const payload = await response.json().catch(() => undefined) as { message?: string; code?: string } | undefined
    throw new ApiError(payload?.message ?? `请求失败（${response.status}）`, response.status, payload?.code)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

/** Multipart uploads reuse the same HttpOnly session and CSRF protections without JSON serialization. */
export async function apiUpload<T>(path: string, body: FormData): Promise<T> {
  if (!path.startsWith('/')) throw new Error('API path must start with /')
  await ensureCsrfToken()
  const headers = new Headers({ Accept: 'application/json' })
  const csrfToken = getCsrfToken()
  if (csrfToken) headers.set('X-CSRF-TOKEN', decodeURIComponent(csrfToken))
  const response = await fetch(`${API_BASE_URL}${path}`, { method: 'POST', headers, body, credentials: 'same-origin' })
  if (!response.ok) {
    const payload = await response.json().catch(() => undefined) as { message?: string; code?: string } | undefined
    throw new ApiError(payload?.message ?? `上传失败（${response.status}）`, response.status, payload?.code)
  }
  return response.json() as Promise<T>
}
