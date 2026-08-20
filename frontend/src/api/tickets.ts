import { ApiError, apiRequest } from '@/api/client'
import { demoTicketRepository } from '@/api/demo-tickets'

export type TicketType = 'INCIDENT' | 'SERVICE_REQUEST' | 'ACCESS_REQUEST' | 'PROBLEM' | 'CHANGE'
export type TicketStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_CLASSIFICATION'
  | 'PENDING_ASSIGNMENT'
  | 'PENDING_ACCEPTANCE'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'PENDING_USER_FEEDBACK'
  | 'CLOSED'
  | 'CANCELLED'
  | 'ON_HOLD'
export type TicketPriority = 'P1' | 'P2' | 'P3' | 'P4'
export type TagKind = 'STANDARD' | 'FREE'
export type TicketLifecycleAction = 'SUBMIT' | 'CLASSIFY' | 'ASSIGN' | 'ACCEPT' | 'START_PROCESSING' | 'REQUEST_USER_FEEDBACK' | 'RESOLVE' | 'CLOSE' | 'REOPEN' | 'CANCEL' | 'HOLD' | 'RESUME' | 'ESCALATE'
export type TicketWorkAction = 'TRANSFER' | 'ADD_COLLABORATOR' | 'REMOVE_COLLABORATOR' | 'CLAIM' | 'APPOINT_PRIMARY' | 'HANDOVER_SHIFT'
export type TicketActionCode = TicketLifecycleAction | TicketWorkAction | 'INTERNAL_COMMENT'

/** Returned by the server's workflow read model. The UI must never infer this from a status. */
export interface TicketAvailableAction {
  code: TicketActionCode
  label?: string
  requiresTarget?: boolean
  disabledReason?: string
}

export interface TicketParticipant {
  role: 'PRIMARY' | 'COLLABORATOR'
  identity: IdentitySnapshot
  assignedAt: string
}

export interface TicketTimelineEvent {
  id: string
  label: string
  occurredAt: string
  note?: string
  actor?: IdentitySnapshot
  auditEventId?: string
}

export interface TicketComment {
  id: string
  visibility: 'INTERNAL'
  author: IdentitySnapshot
  content: string
  createdAt: string
  auditEventId?: string
}

export interface TicketTag {
  name: string
  kind: TagKind
}

export interface IdentitySnapshot {
  iamUserId: string
  displayName: string
  organizationName: string
  positionName?: string
  capturedAt: string
}

export interface ServiceCatalogSummary {
  id: string
  name: string
}

export interface Ticket {
  id: string
  type: TicketType
  status: TicketStatus
  priority: TicketPriority
  title: string
  description?: string
  requester: IdentitySnapshot
  assignee?: IdentitySnapshot
  serviceCatalogItem: ServiceCatalogSummary
  tags?: TicketTag[]
  createdAt: string
  updatedAt?: string
  version: number
  /** Optional until the workflow read model is available on every backend profile. */
  availableActions?: TicketAvailableAction[]
  participants?: TicketParticipant[]
  timeline?: TicketTimelineEvent[]
}

export interface TicketPage {
  items: Ticket[]
  page: number
  pageSize: number
  total: number
}

export interface TicketCreateRequest {
  serviceCatalogItemId: string
  serviceCatalogFormVersion: number
  type: TicketType
  title: string
  description: string
  structuredFields: Record<string, string | boolean | string[]>
  tags?: TicketTag[]
  relatedConfigurationItemIds?: string[]
}

export interface TicketQuery {
  page?: number
  pageSize?: number
  status?: TicketStatus
  type?: TicketType
  q?: string
}

export interface TicketResult<T> {
  data: T
  source: 'api' | 'demo'
}

export interface TicketActionRequest {
  action: TicketLifecycleAction
  version: number
  reason: string
  structuredFields: Record<string, string | boolean | string[]>
}

export interface TicketWorkActionRequest {
  action: TicketWorkAction
  version: number
  reason: string
  targetIamUserId?: string
  structuredFields: Record<string, string | boolean | string[]>
}

export interface TicketActionResult {
  ticket: Ticket
  decision: { outcome: 'COMPLETED' | 'PENDING_APPROVAL' | 'PENDING_PROCESS_TASK'; workflowInstanceId: string; currentNodeCode: string; auditEventId: string }
  slaImpact: { calculatedByServer: true; impact: 'NONE' | 'PAUSED' | 'RESUMED' | 'RECALCULATED' | 'BREACH_RISK'; targetAt?: string }
}

export interface TicketWorkActionResult extends Omit<TicketActionResult, 'slaImpact'> {
  participants: TicketParticipant[]
}

function isConnectionFailure(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && error.status === 503)
}

function createQuery(query: TicketQuery): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const search = params.toString()
  return search ? `?${search}` : ''
}

function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

/**
 * The demo repository is an isolated, in-memory visual fallback. It is only used
 * for connection failures in development, never to derive identity or authorization.
 */
const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'

export const ticketApi = {
  async list(query: TicketQuery = {}): Promise<TicketResult<TicketPage>> {
    try {
      return { data: await apiRequest<TicketPage>(`/tickets${createQuery(query)}`), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoTicketRepository.list(query), source: 'demo' }
    }
  },

  async get(ticketId: string): Promise<TicketResult<Ticket>> {
    try {
      return { data: await apiRequest<Ticket>(`/tickets/${encodeURIComponent(ticketId)}`), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoTicketRepository.get(ticketId), source: 'demo' }
    }
  },

  async create(request: TicketCreateRequest): Promise<TicketResult<Ticket>> {
    try {
      return {
        data: await apiRequest<Ticket>('/tickets', {
          method: 'POST',
          headers: { 'Idempotency-Key': newIdempotencyKey() },
          body: request,
        }),
        source: 'api',
      }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoTicketRepository.create(request), source: 'demo' }
    }
  },

  async executeAction(ticketId: string, request: TicketActionRequest): Promise<TicketActionResult> {
    return apiRequest<TicketActionResult>(`/tickets/${encodeURIComponent(ticketId)}/actions`, {
      method: 'POST', headers: { 'Idempotency-Key': newIdempotencyKey() }, body: request,
    })
  },

  async executeWorkAction(ticketId: string, request: TicketWorkActionRequest): Promise<TicketWorkActionResult> {
    return apiRequest<TicketWorkActionResult>(`/tickets/${encodeURIComponent(ticketId)}/work-actions`, {
      method: 'POST', headers: { 'Idempotency-Key': newIdempotencyKey() }, body: request,
    })
  },

  async listInternalComments(ticketId: string): Promise<TicketPageResult<TicketComment>> {
    return apiRequest<TicketPageResult<TicketComment>>(`/tickets/${encodeURIComponent(ticketId)}/internal-comments?page=1&pageSize=50`)
  },

  async createInternalComment(ticketId: string, request: { version: number; reason: string; content: string; structuredFields?: Record<string, string> }): Promise<TicketComment> {
    return apiRequest<TicketComment>(`/tickets/${encodeURIComponent(ticketId)}/internal-comments`, {
      method: 'POST', headers: { 'Idempotency-Key': newIdempotencyKey() }, body: request,
    })
  },
}

export interface TicketPageResult<T> { items: T[]; page: number; pageSize: number; total: number }
