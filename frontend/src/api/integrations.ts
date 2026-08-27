import { ApiError, apiRequest } from '@/api/client'

export type IntegrationSystemType = 'CMDB' | 'MONITORING' | 'LOG' | 'LOG_PLATFORM' | 'APM' | 'OTHER'
export type IntegrationHealthStatus = 'HEALTHY' | 'DEGRADED' | 'UNAVAILABLE' | 'DISABLED' | 'NOT_CONFIGURED' | 'NOT_CHECKED' | 'CONFIGURATION_PENDING' | 'UNKNOWN'
export type ExternalAlertSeverity = 'CRITICAL' | 'WARNING' | 'INFO'
export type ExternalAlertStatus = 'RECEIVED' | 'PROCESSING' | 'TICKET_CREATED' | 'DEDUPLICATED' | 'SUPPRESSED' | 'RETRY_SCHEDULED' | 'REJECTED' | 'RECOVERED' | 'FAILED' | 'UNKNOWN'
export type AlertIdempotencyStatus = 'CREATED' | 'DEDUPLICATED' | 'SUPPRESSED' | 'RETRY_SCHEDULED' | 'UNKNOWN'

/** Values are server-side summaries only. Endpoint, tenant, credential and payload details are never part of this view model. */
export interface IntegrationConnectionHealth {
  code: string
  systemType: IntegrationSystemType
  enabled: boolean
  healthStatus: IntegrationHealthStatus
  timeoutMs?: number
  rateLimitPerMinute?: number
  lastSuccessAt?: string
}

export interface ExternalAlertSummary {
  alertId: string
  sourceCode: string
  severity: ExternalAlertSeverity
  /** Server-side idempotency/normalization result, never supplied by a browser. */
  status: ExternalAlertStatus
  /** Server-calculated receipt deduplication result; separate from alert business processing status. */
  idempotencyStatus?: AlertIdempotencyStatus
  ticketId?: string
  occurredAt: string
  configurationItemId?: string
  configurationItemName?: string
}

export interface ConfigurationItemSummary {
  id: string
  name: string
  ciType: string
  status: string
  organizationId: string
  sourceCode: string
}

export interface IntegrationOverview {
  connectionHealths: IntegrationConnectionHealth[]
  recentAlerts: ExternalAlertSummary[]
  scopeLabel?: string
}

export interface TrustedDeepLinkRequest {
  systemCode: 'LOG' | 'APM'
  resourceType: 'CONFIGURATION_ITEM'
  resourceId: string
}

interface TrustedDeepLinkWire {
  systemCode: string
  displayName: string
  resourceType: string
  resourceId: string
  url: string
}

export interface TrustedDeepLink extends TrustedDeepLinkWire {
  /** URL accepted by the browser's defense-in-depth policy; absent means do not render a link. */
  safeUrl?: string
}

export interface IntegrationResult<T> {
  data: T
  source: 'api' | 'demo'
}

const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'
const isConnectionFailure = (error: unknown): boolean => error instanceof TypeError || (error instanceof ApiError && error.status === 503)

const demoOverview: IntegrationOverview = {
  scopeLabel: '当前 IAM 数据范围：总部 / 信息技术部',
  connectionHealths: [
    { code: 'CMDB', systemType: 'CMDB', enabled: true, healthStatus: 'HEALTHY', timeoutMs: 1500, rateLimitPerMinute: 120, lastSuccessAt: '2026-08-21T09:42:10+08:00' },
    { code: 'MONITORING', systemType: 'MONITORING', enabled: true, healthStatus: 'HEALTHY', timeoutMs: 2000, rateLimitPerMinute: 60, lastSuccessAt: '2026-08-21T09:41:58+08:00' },
    { code: 'LOG', systemType: 'LOG_PLATFORM', enabled: true, healthStatus: 'DEGRADED', timeoutMs: 3000, rateLimitPerMinute: 40, lastSuccessAt: '2026-08-21T09:38:44+08:00' },
    { code: 'APM', systemType: 'APM', enabled: false, healthStatus: 'NOT_CONFIGURED' },
  ],
  recentAlerts: [
    { alertId: 'ALT-MON-8F2A', sourceCode: 'MONITORING', severity: 'CRITICAL', status: 'TICKET_CREATED', idempotencyStatus: 'CREATED', ticketId: 'TKT-20260820-000421', occurredAt: '2026-08-21T09:35:00+08:00', configurationItemId: 'CI-ERP-ORDER', configurationItemName: 'ERP 采购订单服务' },
    { alertId: 'ALT-MON-8F19', sourceCode: 'MONITORING', severity: 'WARNING', status: 'RECEIVED', idempotencyStatus: 'DEDUPLICATED', ticketId: 'TKT-20260820-000421', occurredAt: '2026-08-21T09:33:00+08:00', configurationItemId: 'CI-ERP-ORDER', configurationItemName: 'ERP 采购订单服务' },
    { alertId: 'ALT-NET-21B7', sourceCode: 'MONITORING', severity: 'WARNING', status: 'PROCESSING', idempotencyStatus: 'RETRY_SCHEDULED', occurredAt: '2026-08-21T09:26:00+08:00', configurationItemId: 'CI-NET-HQ', configurationItemName: '总部园区出口网络' },
  ],
}

const demoConfigurationItems: ConfigurationItemSummary[] = [
  { id: 'CI-ERP-ORDER', name: 'ERP 采购订单服务', ciType: '业务应用', status: '运行中', organizationId: 'ORG-HQ-IT', sourceCode: 'CMDB' },
  { id: 'CI-NET-HQ', name: '总部园区出口网络', ciType: '网络设备', status: '运行中', organizationId: 'ORG-HQ-IT', sourceCode: 'CMDB' },
]

/**
 * No provider URL is accepted from UI state. The backend resolves a reviewed integration
 * configuration and returns a short-lived, authorization-bound navigation URL.
 */
function safeNavigationUrl(url: string): string | undefined {
  try {
    const resolved = new URL(url, window.location.origin)
    if (!['https:', 'http:'].includes(resolved.protocol) || resolved.username || resolved.password) return undefined
    // Plain HTTP is only allowed for the same-origin local development gateway.
    if (resolved.protocol === 'http:' && resolved.origin !== window.location.origin) return undefined
    return resolved.href
  } catch {
    return undefined
  }
}

function toConfigurationItemPage(payload: ConfigurationItemSummary[] | { items?: ConfigurationItemSummary[] }): ConfigurationItemSummary[] {
  return Array.isArray(payload) ? payload : payload.items ?? []
}

export const integrationsApi = {
  async overview(): Promise<IntegrationResult<IntegrationOverview>> {
    try {
      return { data: await apiRequest<IntegrationOverview>('/integrations/operations-overview'), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoOverview, source: 'demo' }
    }
  },

  async configurationItems(organizationId: string): Promise<IntegrationResult<ConfigurationItemSummary[]>> {
    try {
      const query = new URLSearchParams({ organizationId })
      const payload = await apiRequest<ConfigurationItemSummary[] | { items?: ConfigurationItemSummary[] }>(`/integrations/configuration-items?${query}`)
      return { data: toConfigurationItemPage(payload), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoConfigurationItems, source: 'demo' }
    }
  },

  async createTrustedDeepLink(ticketId: string, request: TrustedDeepLinkRequest): Promise<TrustedDeepLink> {
    const link = await apiRequest<TrustedDeepLinkWire>(`/tickets/${encodeURIComponent(ticketId)}/integrations/deep-links`, {
      method: 'POST', body: request,
    })
    return { ...link, safeUrl: safeNavigationUrl(link.url) }
  },
}
