import { ApiError, apiRequest } from '@/api/client'
export interface OperationsReportQuery {
  /** ISO calendar day; the server validates the allowed reporting interval. */
  dateFrom: string
  dateTo: string
}

export interface OperationsReportSummary {
  createdCount?: number
  resolvedCount?: number
  openCount?: number
  resolutionP50Minutes?: number
  resolutionP90Minutes?: number
  averageResponseMinutes?: number
  averageResolutionMinutes?: number
  responseComplianceRate?: number
  resolutionComplianceRate?: number
}

export interface ReportTrendPoint {
  date: string
  createdCount: number
  resolvedCount: number
}

export interface ReportDistributionItem {
  code: string
  label: string
  count: number
}

export interface QueueLoad {
  queueId: string
  queueName: string
  openTicketCount: number
  pendingAcceptanceCount: number
  atRiskCount: number
  breachedCount: number
}

export interface SlaRiskSummary {
  atRiskCount?: number
  breachedCount?: number
}

export interface OperationsReport {
  /** Human-readable server-calculated data scope. It is never a client-selected organization. */
  scopeLabel: string
  generatedAt: string
  summary: OperationsReportSummary
  trend: ReportTrendPoint[]
  statusDistribution: ReportDistributionItem[]
  typeDistribution: ReportDistributionItem[]
  queueLoads: QueueLoad[]
  slaRisk: SlaRiskSummary
}

export interface SlaRuleSummary {
  id: string
  name: string
  enabled: boolean
  serviceCatalogItemName: string
  /** Server-side policy matching precedence, not a client-controlled ticket priority. */
  priorityLabel: string
  responseTargetMinutes: number
  resolutionTargetMinutes: number
  riskThresholdMinutes: number
  calendarName: string
  pauseStatusLabels: string[]
  version: number
  publishedAt: string
}

export interface SlaRulePage {
  items: SlaRuleSummary[]
  total: number
}

interface SlaPolicyWire {
  id: string
  name: string
  version: number
  lifecycleState: 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'SUPERSEDED'
  priority: number
  matchCriteria: { ticketTypes: string[]; serviceCatalogItemIds?: string[] }
  workCalendar: { id: string; version: number }
  targets: Array<{ targetType: 'FIRST_RESPONSE' | 'RESOLUTION'; targetBusinessMinutes: number; warningPercent: number }>
  pauseConditions: Array<{ pauseOnTicketStatuses: string[] }>
  createdAt: string
}

interface SlaPolicyPageWire { items: SlaPolicyWire[]; page: number; pageSize: number; total: number }
interface LegacySlaPolicyWire {
  id: string; name: string; serviceCatalogItemId: string; priority: string; responseTargetMinutes: number; resolutionTargetMinutes: number
  calendarKey: string; pauseStatuses: string[]; active: boolean; version: number; createdAt: string
}

type ReportMetricCode =
  | 'TICKET_CREATED_COUNT' | 'TICKET_RESOLVED_COUNT' | 'OPEN_TICKET_COUNT'
  | 'TICKET_STATUS_DISTRIBUTION' | 'TICKET_TYPE_DISTRIBUTION'
  | 'RESOLUTION_BUSINESS_MINUTES_P50' | 'RESOLUTION_BUSINESS_MINUTES_P90'
  | 'SLA_RESOLUTION_COMPLIANCE_RATE' | 'SLA_FIRST_RESPONSE_COMPLIANCE_RATE'
  | 'SLA_AT_RISK_COUNT' | 'SLA_BREACHED_COUNT' | 'BACKLOG_AGING_COUNT'
interface ReportMetricWire {
  code: ReportMetricCode
  value: number | null
  availability: 'COMPLETE' | 'PARTIAL' | 'NOT_AVAILABLE'
  dimension?: { ticketStatus?: string; ticketType?: string }
}
interface ReportScopeWire { organizationScopeSummary?: string }
interface ReportFreshnessWire { asOf: string; missingDataStatus: 'NONE' | 'PARTIAL' | 'UNAVAILABLE' }
interface OperationsKpiWire { scope: ReportScopeWire; metrics: ReportMetricWire[]; freshness: ReportFreshnessWire }
interface OperationsTrendWire { points: Array<{ bucketStart: string; availability: 'COMPLETE' | 'PARTIAL' | 'NOT_AVAILABLE'; metrics: ReportMetricWire[] }> }
interface OperationsQueueLoadWire { items: Array<{ queueCode: string; queueName?: string; openTicketCount: number; pendingAcceptanceCount: number; atRiskCount: number; breachedCount: number }> }

const statusLabels: Record<string, string> = { PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', PENDING_USER_FEEDBACK: '待用户反馈', RESOLVED: '已解决', CLOSED: '已关闭', ON_HOLD: '已挂起', PENDING_ASSIGNMENT: '待分派', PENDING_CLASSIFICATION: '待分类', SUBMITTED: '已提交' }
const typeLabels: Record<string, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更' }

function metricValue(metrics: ReportMetricWire[], code: ReportMetricCode): number | undefined {
  const item = metrics.find((metric) => metric.code === code && metric.availability !== 'NOT_AVAILABLE')
  return typeof item?.value === 'number' ? item.value : undefined
}
function distributions(metrics: ReportMetricWire[], code: 'TICKET_STATUS_DISTRIBUTION' | 'TICKET_TYPE_DISTRIBUTION'): ReportDistributionItem[] {
  return metrics.filter((metric) => metric.code === code && metric.availability !== 'NOT_AVAILABLE').map((metric) => {
    const key = code === 'TICKET_STATUS_DISTRIBUTION' ? metric.dimension?.ticketStatus : metric.dimension?.ticketType
    return { code: key ?? 'UNKNOWN', label: (code === 'TICKET_STATUS_DISTRIBUTION' ? statusLabels[key ?? ''] : typeLabels[key ?? '']) ?? key ?? '未分类', count: typeof metric.value === 'number' ? metric.value : 0 }
  })
}
function mapOperations(kpi: OperationsKpiWire, trend: OperationsTrendWire, queues: OperationsQueueLoadWire): OperationsReport {
  return {
    scopeLabel: kpi.scope.organizationScopeSummary ?? '当前 IAM 授权数据范围', generatedAt: kpi.freshness.asOf,
    summary: {
      createdCount: metricValue(kpi.metrics, 'TICKET_CREATED_COUNT'), resolvedCount: metricValue(kpi.metrics, 'TICKET_RESOLVED_COUNT'), openCount: metricValue(kpi.metrics, 'OPEN_TICKET_COUNT'),
      resolutionP50Minutes: metricValue(kpi.metrics, 'RESOLUTION_BUSINESS_MINUTES_P50'), resolutionP90Minutes: metricValue(kpi.metrics, 'RESOLUTION_BUSINESS_MINUTES_P90'),
      responseComplianceRate: metricValue(kpi.metrics, 'SLA_FIRST_RESPONSE_COMPLIANCE_RATE'), resolutionComplianceRate: metricValue(kpi.metrics, 'SLA_RESOLUTION_COMPLIANCE_RATE'),
    },
    trend: trend.points.flatMap((point) => {
      const created = metricValue(point.metrics, 'TICKET_CREATED_COUNT'), resolved = metricValue(point.metrics, 'TICKET_RESOLVED_COUNT')
      return point.availability === 'NOT_AVAILABLE' || created === undefined || resolved === undefined ? [] : [{ date: point.bucketStart.slice(5, 10), createdCount: created, resolvedCount: resolved }]
    }),
    statusDistribution: distributions(kpi.metrics, 'TICKET_STATUS_DISTRIBUTION'), typeDistribution: distributions(kpi.metrics, 'TICKET_TYPE_DISTRIBUTION'),
    queueLoads: queues.items.map((item) => ({ queueId: item.queueCode, queueName: item.queueName ?? item.queueCode, openTicketCount: item.openTicketCount, pendingAcceptanceCount: item.pendingAcceptanceCount, atRiskCount: item.atRiskCount, breachedCount: item.breachedCount })),
    slaRisk: { atRiskCount: metricValue(kpi.metrics, 'SLA_AT_RISK_COUNT'), breachedCount: metricValue(kpi.metrics, 'SLA_BREACHED_COUNT') },
  }
}

export interface ReportResult<T> {
  data: T
  source: 'api' | 'demo'
}

function isConnectionFailure(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && error.status === 503)
}

function queryString(query: OperationsReportQuery): string {
  const start = new Date(`${query.dateFrom}T00:00:00`)
  const end = new Date(`${query.dateTo}T00:00:00`)
  end.setDate(end.getDate() + 1)
  const params = new URLSearchParams({ from: start.toISOString(), to: end.toISOString() })
  return `?${params.toString()}`
}

const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'

const demoReport: OperationsReport = {
  scopeLabel: '当前 IAM 数据范围：总部 / 信息技术部 + 服务台队列',
  generatedAt: '2026-08-21T09:30:00+08:00',
  summary: {
    createdCount: 128, resolvedCount: 116, openCount: 37, resolutionP50Minutes: 152, resolutionP90Minutes: 315,
    averageResponseMinutes: 18, averageResolutionMinutes: 152, responseComplianceRate: 96.8, resolutionComplianceRate: 94.1,
  },
  trend: [
    { date: '08-15', createdCount: 22, resolvedCount: 19 }, { date: '08-16', createdCount: 18, resolvedCount: 21 },
    { date: '08-17', createdCount: 16, resolvedCount: 14 }, { date: '08-18', createdCount: 26, resolvedCount: 25 },
    { date: '08-19', createdCount: 21, resolvedCount: 18 }, { date: '08-20', createdCount: 25, resolvedCount: 19 },
  ],
  statusDistribution: [
    { code: 'PENDING_ACCEPTANCE', label: '待受理', count: 12 }, { code: 'IN_PROGRESS', label: '处理中', count: 19 },
    { code: 'PENDING_USER_FEEDBACK', label: '待用户反馈', count: 6 }, { code: 'RESOLVED', label: '已解决', count: 116 },
  ],
  typeDistribution: [
    { code: 'INCIDENT', label: '故障报修', count: 73 }, { code: 'SERVICE_REQUEST', label: '服务请求', count: 31 },
    { code: 'ACCESS_REQUEST', label: '账号权限', count: 19 }, { code: 'PROBLEM', label: '问题管理', count: 5 },
  ],
  queueLoads: [
    { queueId: 'QUEUE-DESK-01', queueName: '服务台一线队列', openTicketCount: 12, pendingAcceptanceCount: 3, atRiskCount: 1, breachedCount: 0 },
    { queueId: 'QUEUE-APP-02', queueName: '应用运维队列', openTicketCount: 16, pendingAcceptanceCount: 2, atRiskCount: 4, breachedCount: 1 },
    { queueId: 'QUEUE-NET-03', queueName: '网络运维队列', openTicketCount: 9, pendingAcceptanceCount: 1, atRiskCount: 2, breachedCount: 1 },
  ],
  slaRisk: { atRiskCount: 7, breachedCount: 2 },
}

const demoRules: SlaRulePage = {
  total: 3,
  items: [
    { id: 'SLA-INC-P1', name: 'P1 重大故障', enabled: true, serviceCatalogItemName: '故障报修 / 通用', priorityLabel: 'P1', responseTargetMinutes: 15, resolutionTargetMinutes: 240, riskThresholdMinutes: 60, calendarName: '7×24 服务日历', pauseStatusLabels: ['待用户反馈', '挂起（已审批）'], version: 3, publishedAt: '2026-08-18T16:30:00+08:00' },
    { id: 'SLA-INC-P2', name: 'P2 一般故障', enabled: true, serviceCatalogItemName: '业务系统 - 页面性能问题', priorityLabel: 'P2', responseTargetMinutes: 30, resolutionTargetMinutes: 480, riskThresholdMinutes: 120, calendarName: '工作日服务日历', pauseStatusLabels: ['待用户反馈', '挂起（已审批）'], version: 4, publishedAt: '2026-08-19T10:00:00+08:00' },
    { id: 'SLA-REQ-P3', name: 'P3 服务请求', enabled: true, serviceCatalogItemName: '软件服务 - 白名单软件安装', priorityLabel: 'P3', responseTargetMinutes: 240, resolutionTargetMinutes: 1440, riskThresholdMinutes: 240, calendarName: '工作日服务日历', pauseStatusLabels: ['待用户反馈'], version: 2, publishedAt: '2026-08-15T09:00:00+08:00' },
  ],
}

/**
 * Reporting data is always server-filtered by the IAM data scope. Demo data is
 * an isolated development preview only and is never used after authorization failures.
 */
export const reportsApi = {
  async operations(query: OperationsReportQuery): Promise<ReportResult<OperationsReport>> {
    try {
      const queryPart = queryString(query)
      const [kpi, trend, queue] = await Promise.all([
        apiRequest<OperationsKpiWire>(`/reports/operations/kpis${queryPart}`),
        apiRequest<OperationsTrendWire>(`/reports/operations/trends${queryPart}&granularity=DAY`),
        apiRequest<OperationsQueueLoadWire>(`/reports/operations/queue-load${queryPart}`),
      ])
      return { data: mapOperations(kpi, trend, queue), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoReport, source: 'demo' }
    }
  },

  async slaRules(): Promise<ReportResult<SlaRulePage>> {
    try {
      const response = await apiRequest<SlaPolicyPageWire | LegacySlaPolicyWire[]>('/admin/sla/policies?page=1&pageSize=50')
      const page: SlaPolicyPageWire = Array.isArray(response) ? { items: [], page: 1, pageSize: 50, total: response.length } : response
      if (Array.isArray(response)) {
        return { data: { total: response.length, items: response.map((policy) => ({
          id: policy.id, name: policy.name, enabled: policy.active, serviceCatalogItemName: policy.serviceCatalogItemId,
          priorityLabel: policy.priority, responseTargetMinutes: policy.responseTargetMinutes, resolutionTargetMinutes: policy.resolutionTargetMinutes,
          riskThresholdMinutes: 0, calendarName: policy.calendarKey, pauseStatusLabels: policy.pauseStatuses, version: policy.version, publishedAt: policy.createdAt,
        })) }, source: 'api' }
      }
      return {
        data: {
          total: page.total,
          items: page.items.map((policy) => {
            const response = policy.targets.find((target) => target.targetType === 'FIRST_RESPONSE')
            const resolution = policy.targets.find((target) => target.targetType === 'RESOLUTION')
            const threshold = Math.min(...policy.targets.map((target) => Math.round(target.targetBusinessMinutes * (100 - target.warningPercent) / 100)))
            return {
              id: policy.id, name: policy.name, enabled: policy.lifecycleState === 'PUBLISHED', priorityLabel: `策略优先级 ${policy.priority}`,
              serviceCatalogItemName: policy.matchCriteria.serviceCatalogItemIds?.length ? `受控目录 ${policy.matchCriteria.serviceCatalogItemIds.join('、')}` : `工单类型 ${policy.matchCriteria.ticketTypes.join('、')}`,
              responseTargetMinutes: response?.targetBusinessMinutes ?? 0, resolutionTargetMinutes: resolution?.targetBusinessMinutes ?? 0,
              riskThresholdMinutes: Number.isFinite(threshold) ? threshold : 0,
              calendarName: `${policy.workCalendar.id} · v${policy.workCalendar.version}`,
              pauseStatusLabels: [...new Set(policy.pauseConditions.flatMap((condition) => condition.pauseOnTicketStatuses))],
              version: policy.version, publishedAt: policy.createdAt,
            }
          }),
        }, source: 'api',
      }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoRules, source: 'demo' }
    }
  },
}
