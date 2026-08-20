import { ApiError, apiRequest } from '@/api/client'

export type NotificationCategory = 'TICKET' | 'WORKFLOW' | 'SLA' | 'SYSTEM' | 'INTEGRATION'
export type NotificationReadState = 'UNREAD' | 'READ'
export type NotificationChannel = 'IN_APP' | 'WPS_IM' | 'WECHAT_WORK'
export type NotificationDeliveryState = 'PENDING' | 'DELIVERING' | 'DELIVERED' | 'RETRY_SCHEDULED' | 'FAILED_FINAL' | 'SUPPRESSED'
export type NotificationRoutingEvent = 'TICKET_CREATED' | 'TICKET_ASSIGNED' | 'TICKET_STATUS_CHANGED' | 'WORKFLOW_TASK_CREATED' | 'WORKFLOW_TASK_REMINDER' | 'SLA_BREACH_RISK' | 'SLA_BREACHED' | 'SYSTEM_ANNOUNCEMENT' | 'INTEGRATION_ALERT'
export type NotificationRoutingRuleLifecycleStatus = 'DRAFT' | 'PENDING_PUBLICATION' | 'PUBLISHED' | 'DISABLED' | 'ARCHIVED'

/** Current-recipient notification projection defined by the API contract. */
export interface NotificationItem {
  id: string
  category: NotificationCategory
  title: string
  body: string
  sourceType?: 'TICKET' | 'WORKFLOW_TASK' | 'SLA_POLICY' | 'SYSTEM_EVENT' | 'INTEGRATION_EVENT'
  sourceDisplayReference?: string
  readState: NotificationReadState
  readAt?: string
  createdAt: string
  version: number
}

export interface NotificationPage {
  items: NotificationItem[]
  page: number
  pageSize: number
  total: number
}

export interface NotificationDelivery {
  id: string
  channel: NotificationChannel
  state: NotificationDeliveryState
  attemptCount: number
  lastAttemptAt?: string
  nextRetryAt?: string
  terminalReasonCode?: 'RECIPIENT_UNAVAILABLE' | 'CHANNEL_DISABLED' | 'POLICY_SUPPRESSED' | 'PROVIDER_REJECTED' | 'RETRY_EXHAUSTED'
  createdAt: string
  deliveredAt?: string
}

export interface NotificationDeliveryPage { items: NotificationDelivery[]; page: number; pageSize: number; total: number }
export interface NotificationQuery { page?: number; pageSize?: number; readState?: NotificationReadState; category?: NotificationCategory }
export interface NotificationResult<T> { data: T; source: 'api' | 'demo' }
export interface TicketNotificationSummary { unreadCount: number; latest?: Pick<NotificationItem, 'id' | 'title' | 'body' | 'createdAt' | 'readAt'> }

/** Deliberately minimal: no recipient, address, condition expression, endpoint or secret. */
export interface NotificationRoutingRuleMatchSummary { id: string; version: number; priority: number; aggregationWindowSeconds: number; includeDescendants: boolean; lifecycleStatus: NotificationRoutingRuleLifecycleStatus }
export interface NotificationRoutingPreview {
  organizationIamOrganizationId: string
  event: NotificationRoutingEvent
  resolution: string
  requestedChannel?: NotificationChannel
  resolvedChannel?: NotificationChannel
  inAppFallbackApplied: boolean
  matchedRule?: NotificationRoutingRuleMatchSummary
}

const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
const isConnectionFailure = (error: unknown): boolean => error instanceof TypeError || (error instanceof ApiError && error.status === 503)
const newIdempotencyKey = (): string => crypto.randomUUID()

function queryString(query: Record<string, string | number | boolean | undefined>): string {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') params.set(key, String(value))
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

function matchesTicket(item: NotificationItem, ticketId: string): boolean {
  // Display reference is not an authorization credential; opening the ticket always re-checks server authorization.
  return new RegExp(`\\b${ticketId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`).test(item.sourceDisplayReference ?? '')
}

export function extractTicketId(item: NotificationItem): string | undefined {
  return item.sourceDisplayReference?.match(/\bTKT-\d{8}-\d{6}\b/)?.[0]
}

let demoItems: NotificationItem[] = [
  { id: 'NTF-demo-00017', category: 'WORKFLOW', title: '待受理：ERP 页面性能问题', body: '工单已分派至您的候选队列，请在处理页面确认受理。', sourceType: 'WORKFLOW_TASK', sourceDisplayReference: 'TKT-20260819-000421 · ERP 采购订单页面加载缓慢', readState: 'UNREAD', createdAt: '2026-08-20T09:18:00+08:00', version: 1 },
  { id: 'NTF-demo-00016', category: 'SLA', title: 'SLA 风险提醒：剩余 30 分钟', body: '当前处理节点接近目标时限，请关注处理进展。', sourceType: 'SLA_POLICY', sourceDisplayReference: 'TKT-20260819-000421 · ERP 采购订单页面加载缓慢', readState: 'UNREAD', createdAt: '2026-08-20T08:42:00+08:00', version: 2 },
  { id: 'NTF-demo-00068', category: 'TICKET', title: '工单状态已更新', body: '工单已进入待受理状态。', sourceType: 'TICKET', sourceDisplayReference: 'TKT-20260818-000380 · 申请财务共享系统报表查看权限', readState: 'READ', readAt: '2026-08-19T14:12:00+08:00', createdAt: '2026-08-19T14:10:00+08:00', version: 3 },
]

function demoList(query: NotificationQuery): NotificationPage {
  const filtered = demoItems.filter((item) => (!query.readState || item.readState === query.readState) && (!query.category || item.category === query.category))
  const page = query.page ?? 1
  const pageSize = query.pageSize ?? 50
  return { items: filtered.slice((page - 1) * pageSize, page * pageSize), page, pageSize, total: filtered.length }
}

/** Browser never controls recipients, channel credentials, delivery policy or authorization. */
export const notificationApi = {
  async list(query: NotificationQuery = {}): Promise<NotificationResult<NotificationPage>> {
    try { return { data: await apiRequest<NotificationPage>(`/notifications${queryString({ ...query })}`), source: 'api' } }
    catch (error) { if (!canUseDemoFallback || !isConnectionFailure(error)) throw error; return { data: demoList(query), source: 'demo' } }
  },

  async unreadCount(): Promise<NotificationResult<{ unreadCount: number }>> {
    const result = await this.list({ page: 1, pageSize: 1, readState: 'UNREAD' })
    return { data: { unreadCount: result.data.total }, source: result.source }
  },

  async markRead(notification: Pick<NotificationItem, 'id' | 'version'>): Promise<NotificationResult<NotificationItem>> {
    try {
      return { data: await apiRequest<NotificationItem>(`/notifications/${encodeURIComponent(notification.id)}/read`, { method: 'PATCH', headers: { 'Idempotency-Key': newIdempotencyKey() }, body: { version: notification.version } }), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      const index = demoItems.findIndex((item) => item.id === notification.id)
      if (index < 0) throw new Error('演示通知不存在')
      const current = demoItems[index]
      const updated = current.readState === 'READ' ? current : { ...current, readState: 'READ' as const, readAt: new Date().toISOString(), version: current.version + 1 }
      demoItems = demoItems.map((item, itemIndex) => itemIndex === index ? updated : item)
      return { data: updated, source: 'demo' }
    }
  },

  async listDeliveries(notificationId: string): Promise<NotificationResult<NotificationDeliveryPage>> {
    try { return { data: await apiRequest<NotificationDeliveryPage>(`/notifications/${encodeURIComponent(notificationId)}/deliveries?page=1&pageSize=20`), source: 'api' } }
    catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: { items: [{ id: `NDL-${notificationId}-1`, channel: 'IN_APP', state: 'DELIVERED', attemptCount: 1, createdAt: '2026-08-20T09:18:00+08:00', deliveredAt: '2026-08-20T09:18:01+08:00' }, { id: `NDL-${notificationId}-2`, channel: 'WPS_IM', state: 'SUPPRESSED', attemptCount: 0, createdAt: '2026-08-20T09:18:00+08:00', terminalReasonCode: 'CHANNEL_DISABLED' }], page: 1, pageSize: 20, total: 2 }, source: 'demo' }
    }
  },

  async ticketSummary(ticketId: string): Promise<NotificationResult<TicketNotificationSummary>> {
    const result = await this.list({ page: 1, pageSize: 100 })
    const related = result.data.items.filter((item) => matchesTicket(item, ticketId))
    const latest = related[0]
    return { data: { unreadCount: related.filter((item) => item.readState === 'UNREAD').length, latest: latest && { id: latest.id, title: latest.title, body: latest.body, createdAt: latest.createdAt, readAt: latest.readAt } }, source: result.source }
  },

  async previewRoutingRule(organizationIamOrganizationId: string, event: NotificationRoutingEvent): Promise<NotificationResult<NotificationRoutingPreview>> {
    try { return { data: await apiRequest<NotificationRoutingPreview>(`/admin/notification-routing-rules/preview${queryString({ organizationIamOrganizationId, event })}`), source: 'api' } }
    catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: { organizationIamOrganizationId, event, resolution: 'MATCHED', requestedChannel: 'WPS_IM', resolvedChannel: 'IN_APP', inAppFallbackApplied: true, matchedRule: { id: 'NRR-DEMO-001', version: 3, priority: 100, aggregationWindowSeconds: 60, includeDescendants: true, lifecycleStatus: 'PUBLISHED' } }, source: 'demo' }
    }
  },
}
