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
}

export interface TicketPage {
  items: Ticket[]
  page: number
  pageSize: number
  total: number
}

export interface TicketCreateRequest {
  serviceCatalogItemId: string
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
}
