import { apiRequest } from '@/api/client'

export type LifecycleGovernedAction = 'HOLD' | 'ESCALATE' | 'CANCEL' | 'REOPEN' | 'ASSIGN' | 'ACCEPT' | 'RESOLVE' | 'CLOSE'
export type PolicyStatus = 'DRAFT' | 'PUBLISHED' | 'RETIRED'
export type DecisionMode = 'ANY_ONE' | 'ALL_OF' | 'QUORUM'

export interface LifecycleApprovalPolicy {
  id: string; name: string; action: LifecycleGovernedAction; serviceCatalogItemId?: string; priority?: 'P1' | 'P2' | 'P3' | 'P4'
  candidateRoles: Array<'ROLE_SERVICE_MANAGER' | 'ROLE_PLATFORM_ADMIN'>; decisionMode: DecisionMode; approvalThresholdPercent: number
  timeoutMinutes: number; timeoutPolicyVersion: string; escalationPolicyVersion: string; status: PolicyStatus; version: number
  createdAt: string; updatedAt: string; publishedAt?: string
}
export interface LifecycleApprovalPolicyInput {
  name: string; action: LifecycleGovernedAction; serviceCatalogItemId?: string; priority?: 'P1' | 'P2' | 'P3' | 'P4'
  candidateRoles: Array<'ROLE_SERVICE_MANAGER' | 'ROLE_PLATFORM_ADMIN'>; decisionMode: DecisionMode; approvalThresholdPercent: number
  timeoutMinutes: number; timeoutPolicyVersion: string; escalationPolicyVersion: string; expectedVersion?: number
}
const root = '/admin/workflow/lifecycle-approval-policies'
export const lifecycleApprovalPolicyApi = {
  list: (): Promise<LifecycleApprovalPolicy[]> => apiRequest(root),
  create: (input: LifecycleApprovalPolicyInput): Promise<LifecycleApprovalPolicy> => apiRequest(root, { method: 'POST', body: input }),
  update: (id: string, input: LifecycleApprovalPolicyInput): Promise<LifecycleApprovalPolicy> => apiRequest(`${root}/${encodeURIComponent(id)}`, { method: 'PUT', body: input }),
  publish: (id: string, expectedVersion: number): Promise<LifecycleApprovalPolicy> => apiRequest(`${root}/${encodeURIComponent(id)}/publish?expectedVersion=${expectedVersion}`, { method: 'POST' }),
  retire: (id: string, expectedVersion: number): Promise<LifecycleApprovalPolicy> => apiRequest(`${root}/${encodeURIComponent(id)}/retire?expectedVersion=${expectedVersion}`, { method: 'POST' }),
}
