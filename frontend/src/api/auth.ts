import { apiRequest } from '@/api/client'
import type { CurrentUserResponse, PlatformRole } from '@/api/identity'

export interface LocalAccount {
  id: string
  loginName: string
  displayName: string
  organizationId: string
  enabled: boolean
  lockedUntil?: string
  failedLoginCount: number
  roles: PlatformRole[]
  systemCodes: string[]
  version: number
  updatedAt: string
}
export interface LocalAccountPage { items: LocalAccount[]; page: number; pageSize: number; total: number }
export interface LocalAccountCreate {
  loginName: string; displayName: string; organizationId: string; password: string
  roles: PlatformRole[]; systemCodes: string[]; reason: string
}
export interface LocalAccountUpdate {
  version: number; displayName: string; organizationId: string; enabled: boolean
  roles: PlatformRole[]; systemCodes: string[]; reason: string
}
const writeHeaders = (version?: number) => ({
  'Idempotency-Key': crypto.randomUUID(),
  ...(version === undefined ? {} : { 'If-Match': `"${version}"` }),
})
export const authApi = {
  login(loginName: string, password: string): Promise<CurrentUserResponse> {
    return apiRequest<CurrentUserResponse>('/auth/login', { method: 'POST', body: { loginName, password } })
  },
  logout(): Promise<void> { return apiRequest<void>('/auth/logout', { method: 'POST' }) },
  accounts(params: { page: number; pageSize: number; q?: string; status?: string }): Promise<LocalAccountPage> {
    const query = new URLSearchParams({ page: String(params.page), pageSize: String(params.pageSize) })
    if (params.q) query.set('q', params.q)
    if (params.status) query.set('status', params.status)
    return apiRequest<LocalAccountPage>(`/admin/local-accounts?${query}`)
  },
  createAccount(input: LocalAccountCreate): Promise<LocalAccount> {
    return apiRequest<LocalAccount>('/admin/local-accounts', { method: 'POST', headers: writeHeaders(), body: input })
  },
  updateAccount(id: string, input: LocalAccountUpdate): Promise<LocalAccount> {
    return apiRequest<LocalAccount>(`/admin/local-accounts/${encodeURIComponent(id)}`, { method: 'PUT', headers: writeHeaders(input.version), body: input })
  },
  resetPassword(id: string, version: number, password: string, reason: string): Promise<LocalAccount> {
    return apiRequest<LocalAccount>(`/admin/local-accounts/${encodeURIComponent(id)}/password-reset`, { method: 'POST', headers: writeHeaders(version), body: { version, password, reason } })
  },
  disableAccount(id: string, version: number, reason: string): Promise<void> {
    return apiRequest<void>(`/admin/local-accounts/${encodeURIComponent(id)}`, { method: 'DELETE', headers: writeHeaders(version), body: { version, reason } })
  },
}
