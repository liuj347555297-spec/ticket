import { ApiError, apiRequest } from '@/api/client'

export type ProjectionUserStatus = 'ACTIVE' | 'DISABLED'
export type PlatformRole =
  | 'REQUESTER'
  | 'FIRST_LINE_SUPPORT'
  | 'SECOND_LINE_SUPPORT'
  | 'APPROVER'
  | 'SERVICE_MANAGER'
  | 'PLATFORM_ADMIN'
  | 'AUDITOR'
export type DataScopeType = 'ORGANIZATION' | 'SERVICE' | 'QUEUE' | 'CONFIGURATION_ITEM'

export interface OrganizationSummary {
  iamOrganizationId: string
  name: string
}

export interface UserProjection {
  iamUserId: string
  displayName: string
  loginName?: string
  status: ProjectionUserStatus
  organization: OrganizationSummary
}

export interface DataScopeSummary {
  scopeType: DataScopeType
  scopeId: string
}

export interface CurrentUserResponse {
  user: UserProjection
  authorization: {
    roles: PlatformRole[]
    dataScopes: DataScopeSummary[]
  }
}

export type CurrentUserSource = 'api' | 'development-preview' | 'unauthenticated'

export interface CurrentUserResult {
  data: CurrentUserResponse | null
  source: CurrentUserSource
}

const canUseDevelopmentPreview = import.meta.env.DEV && import.meta.env.VITE_IAM_DEMO_MODE !== 'false'

const developmentPreview: CurrentUserResponse = {
  user: {
    iamUserId: 'iam-preview-10086',
    displayName: '开发预览用户',
    loginName: 'dev.preview',
    status: 'ACTIVE',
    organization: { iamOrganizationId: 'ORG-HQ-IT', name: '总部 / 信息技术部' },
  },
  authorization: {
    roles: ['REQUESTER', 'FIRST_LINE_SUPPORT'],
    dataScopes: [
      { scopeType: 'ORGANIZATION', scopeId: 'ORG-HQ-IT' },
      { scopeType: 'QUEUE', scopeId: 'QUEUE-DESK-01' },
    ],
  },
}

function canFallbackToPreview(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && [404, 501, 503].includes(error.status))
}

/**
 * Reads the backend's current IAM projection through the same-origin session.
 * This is presentation data only: every protected operation remains authorized
 * by the backend, regardless of the roles/scopes returned here.
 */
export const identityApi = {
  async getCurrentUser(): Promise<CurrentUserResult> {
    try {
      return { data: await apiRequest<CurrentUserResponse>('/me'), source: 'api' }
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        return { data: null, source: 'unauthenticated' }
      }
      if (canUseDevelopmentPreview && canFallbackToPreview(error)) {
        return { data: developmentPreview, source: 'development-preview' }
      }
      throw error
    }
  },
}
