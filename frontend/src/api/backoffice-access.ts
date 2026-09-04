import { apiRequest } from '@/api/client'

export type BackofficeRole = 'ROLE_FIRST_LINE_SUPPORT' | 'ROLE_SECOND_LINE_SUPPORT' | 'ROLE_APPROVER' | 'ROLE_SERVICE_MANAGER' | 'ROLE_SLA_MANAGER' | 'ROLE_AUDITOR' | 'ROLE_PLATFORM_ADMIN'
export type BackofficeScopeType = 'ORGANIZATION' | 'SERVICE' | 'SERVICE_CATALOG' | 'SERVICE_SYSTEM' | 'CONFIGURATION_ITEM'

export interface BackofficeDataScope { scopeType: BackofficeScopeType; scopeId: string }
export interface BackofficeAccessResponse {
  user: { iamUserId: string; loginName: string; displayName: string; organizationIamId: string; organizationName: string }
  access: { enabled: boolean; roleCodes: BackofficeRole[]; dataScopes: BackofficeDataScope[]; version: number; updatedAt: string | null }
}
export interface BackofficeAccessWriteRequest { enabled: boolean; roleCodes: BackofficeRole[]; dataScopes: BackofficeDataScope[]; expectedVersion: number }

/** Backoffice authorization is local platform configuration, never a write to IAM. */
export const backofficeAccessApi = {
  get(iamUserId: string): Promise<BackofficeAccessResponse> {
    return apiRequest(`/admin/backoffice-access/${encodeURIComponent(iamUserId)}`)
  },
  replace(iamUserId: string, request: BackofficeAccessWriteRequest): Promise<BackofficeAccessResponse> {
    return apiRequest(`/admin/backoffice-access/${encodeURIComponent(iamUserId)}`, { method: 'PUT', body: request })
  },
}
