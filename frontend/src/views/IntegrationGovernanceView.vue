<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import {
  integrationsApi,
  type ConfigurationItemSummary,
  type AlertIdempotencyStatus,
  type ExternalAlertStatus,
  type IntegrationHealthStatus,
  type IntegrationOverview,
  type IntegrationSystemType,
  type TrustedDeepLink,
} from '@/api/integrations'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const overview = ref<IntegrationOverview>()
const configurationItems = ref<ConfigurationItemSummary[]>([])
const source = ref<'api' | 'demo'>('api')
const loading = ref(false)
const error = ref('')
const openingLinkKey = ref('')
const linkError = ref('')
const issuedLinks = ref<Record<string, TrustedDeepLink>>({})

const systemLabels: Record<IntegrationSystemType, string> = { CMDB: 'CMDB', MONITORING: '监控告警', LOG: '日志平台', LOG_PLATFORM: '日志平台', APM: '应用性能监控', OTHER: '其他集成' }
const healthLabels: Record<IntegrationHealthStatus, string> = { HEALTHY: '正常', DEGRADED: '降级', UNAVAILABLE: '不可用', DISABLED: '已禁用', NOT_CONFIGURED: '未配置', NOT_CHECKED: '待检测', CONFIGURATION_PENDING: '配置待发布', UNKNOWN: '未知' }
const alertStatusLabels: Record<ExternalAlertStatus, string> = { RECEIVED: '已接收', PROCESSING: '处理中', TICKET_CREATED: '已关联工单', DEDUPLICATED: '已去重关联', SUPPRESSED: '已抑制', RETRY_SCHEDULED: '待重试', REJECTED: '已拒绝', RECOVERED: '已恢复', FAILED: '处理失败', UNKNOWN: '状态未知' }
const idempotencyLabels: Record<AlertIdempotencyStatus, string> = { CREATED: '首次接收', DEDUPLICATED: '重复去重', SUPPRESSED: '策略抑制', RETRY_SCHEDULED: '待重试', UNKNOWN: '未返回' }
const trustedTicketId = /^[A-Z][A-Z0-9_-]{1,63}$/

const configuredConnectionCount = computed(() => overview.value?.connectionHealths.filter((item) => item.enabled).length ?? 0)
const unavailableConnectionCount = computed(() => overview.value?.connectionHealths.filter((item) => ['UNAVAILABLE', 'DEGRADED'].includes(item.healthStatus)).length ?? 0)
const alertTicketCount = computed(() => new Set(overview.value?.recentAlerts.map((item) => item.ticketId).filter(Boolean)).size)
const scopeLabel = computed(() => overview.value?.scopeLabel ?? '由服务端按当前 IAM 数据范围过滤')

function formatTime(value?: string): string {
  if (!value) return '未返回'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '未返回' : new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(date)
}

function healthClass(status: IntegrationHealthStatus): string { return `integration-health--${status.toLowerCase()}` }
function alertClass(status: ExternalAlertStatus | AlertIdempotencyStatus): string { return `integration-alert-status--${status.toLowerCase()}` }
function safeTicket(ticketId?: string): boolean { return Boolean(ticketId && trustedTicketId.test(ticketId)) }
function relatedAlertCount(item: ConfigurationItemSummary): number {
  return overview.value?.recentAlerts.filter((alert) => alert.configurationItemId === item.id).length ?? 0
}
function relatedTicketCount(item: ConfigurationItemSummary): number {
  return new Set(overview.value?.recentAlerts.filter((alert) => alert.configurationItemId === item.id).map((alert) => alert.ticketId).filter(Boolean)).size
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    if (!session.currentUser && !session.loading) await session.loadCurrentUser()
    const overviewResult = await integrationsApi.overview()
    overview.value = overviewResult.data
    source.value = overviewResult.source
    if (!session.currentUser) {
      configurationItems.value = []
      return
    }
    const itemsResult = await integrationsApi.configurationItems(session.currentUser.organizationIamOrganizationId)
    configurationItems.value = itemsResult.data
    if (overviewResult.source === 'demo' || itemsResult.source === 'demo') source.value = 'demo'
  } catch (cause) {
    overview.value = undefined
    configurationItems.value = []
    error.value = cause instanceof ApiError ? cause.message : '运行治理数据暂不可用，请检查服务端授权或稍后重试。'
  } finally {
    loading.value = false
  }
}

async function openTrustedLink(ticketId: string | undefined, item: ConfigurationItemSummary, systemCode: 'LOG' | 'APM'): Promise<void> {
  if (!safeTicket(ticketId)) {
    linkError.value = '当前没有可授权的关联工单，不能生成外部诊断链接。'
    return
  }
  const key = `${ticketId}-${item.id}-${systemCode}`
  openingLinkKey.value = key
  linkError.value = ''
  try {
    const link = await integrationsApi.createTrustedDeepLink(ticketId!, { systemCode, resourceType: 'CONFIGURATION_ITEM', resourceId: item.id })
    if (!link.safeUrl) {
      linkError.value = '服务端返回的链接未通过浏览器安全校验，已阻止打开并保留服务端审计。'
      return
    }
    issuedLinks.value = { ...issuedLinks.value, [key]: link }
    window.open(link.safeUrl, '_blank', 'noopener,noreferrer')
  } catch (cause) {
    linkError.value = cause instanceof ApiError ? cause.message : '无法生成受信任诊断链接，请确认当前工单与 CI 的访问权限。'
  } finally {
    openingLinkKey.value = ''
  }
}

function primaryTicketFor(item: ConfigurationItemSummary): string | undefined {
  return overview.value?.recentAlerts.find((alert) => alert.configurationItemId === item.id && safeTicket(alert.ticketId))?.ticketId
}

onMounted(load)
</script>

<template>
  <div class="page-heading">
    <div><h2>运行治理与外部集成</h2><p>只读查看 CMDB、监控、日志与 APM 的脱敏摘要；访问外部平台始终经后端重新鉴权。</p></div>
    <button class="button button--secondary" type="button" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新状态' }}</button>
  </div>
  <p class="integration-notice">本页不展示或编辑密钥、租户、接口地址、回调地址、原始告警内容或日志正文；告警、CI 与链接均由服务端按 IAM 数据范围、对象权限和受管配置裁决。</p>
  <p v-if="source === 'demo'" class="demo-notice">开发演示数据：仅在集成 API 确实不可用时用于页面预览，不代表生产 CI、告警或连接状态。</p>
  <p v-if="error" class="form-alert form-alert--error">{{ error }}</p>
  <p v-if="loading && !overview" class="panel compact-loading">正在读取当前权限范围内的集成摘要…</p>

  <template v-if="overview">
    <div class="metric-grid integration-metrics">
      <article class="metric-card"><span>已启用连接</span><strong>{{ configuredConnectionCount }}</strong><small class="text-blue">仅显示连接健康摘要</small></article>
      <article class="metric-card"><span>降级 / 不可用</span><strong>{{ unavailableConnectionCount }}</strong><small :class="unavailableConnectionCount ? 'text-orange' : 'text-green'">不会阻断人工建单与处理</small></article>
      <article class="metric-card"><span>近期待关联告警</span><strong>{{ overview.recentAlerts.length }}</strong><small>按来源指纹由服务端归一化</small></article>
      <article class="metric-card"><span>关联工单</span><strong>{{ alertTicketCount }}</strong><small>打开工单时再次对象鉴权</small></article>
    </div>

    <section class="panel integration-scope-panel"><div><b>当前数据范围</b><span>{{ scopeLabel }}</span></div><small>组织、服务、队列与 CI 可见范围由服务端逐请求校验；前端不能切换或扩大范围。</small></section>

    <section class="panel table-panel integration-section"><div class="panel-header"><div><h3>外部告警关联</h3><p>仅展示已归一化的来源、级别、接收幂等结果和关联工单，不展示原始告警正文。</p></div><span class="readonly-badge">服务端归一化</span></div>
      <div v-if="overview.recentAlerts.length" class="table-scroll"><table><thead><tr><th>告警摘要</th><th>来源</th><th>级别</th><th>CI</th><th>接收幂等结果</th><th>处理状态</th><th>关联工单</th><th>发生时间</th></tr></thead><tbody><tr v-for="alert in overview.recentAlerts" :key="alert.alertId"><td><b class="mono-text">{{ alert.alertId }}</b></td><td>{{ alert.sourceCode }}</td><td><span class="tag" :class="alert.severity === 'CRITICAL' ? 'tag--red' : alert.severity === 'WARNING' ? 'tag--orange' : 'tag--blue'">{{ alert.severity === 'CRITICAL' ? '严重' : alert.severity === 'WARNING' ? '告警' : '提示' }}</span></td><td><span v-if="alert.configurationItemName">{{ alert.configurationItemName }}</span><span v-else class="table-subtext">未返回 CI</span></td><td><span v-if="alert.idempotencyStatus" class="integration-alert-status" :class="alertClass(alert.idempotencyStatus)">{{ idempotencyLabels[alert.idempotencyStatus] }}</span><span v-else class="table-subtext">服务端暂未返回</span></td><td><span class="integration-alert-status" :class="alertClass(alert.status)">{{ alertStatusLabels[alert.status] }}</span></td><td><RouterLink v-if="safeTicket(alert.ticketId)" class="ticket-id" :to="`/tickets/${alert.ticketId}`">{{ alert.ticketId }}</RouterLink><span v-else class="table-subtext">未关联 / 无权查看</span></td><td>{{ formatTime(alert.occurredAt) }}</td></tr></tbody></table></div>
      <div v-else class="compact-empty"><div class="empty-icon">◌</div><h3>暂无可见外部告警</h3><p>未配置来源、没有命中当前权限范围，或告警已被服务端策略抑制时，不以空白记录替代。</p></div>
    </section>

    <section class="panel table-panel integration-section"><div class="panel-header"><div><h3>配置项（CI）与影响范围</h3><p>CI 为 CMDB 只读投影；关联告警数和工单数仅依据当前可见告警计算。</p></div><span class="readonly-badge">只读投影</span></div>
      <div v-if="!session.currentUser" class="compact-empty"><div class="empty-icon">◎</div><h3>需要 IAM 会话</h3><p>识别当前组织后，服务端才会返回该组织范围内可见的 CI；前端不会请求或展示全量 CMDB。</p></div>
      <div v-else-if="configurationItems.length" class="table-scroll"><table><thead><tr><th>CI</th><th>类型 / 状态</th><th>CMDB 来源</th><th>当前组织范围内告警</th><th>关联工单</th><th>受信任诊断入口</th></tr></thead><tbody><tr v-for="item in configurationItems" :key="item.id"><td><b>{{ item.name }}</b><span class="table-subtext mono-text">{{ item.id }}</span></td><td>{{ item.ciType }}<span class="table-subtext">{{ item.status }}</span></td><td>{{ item.sourceCode }}</td><td>{{ relatedAlertCount(item) }}<span class="table-subtext">影响范围以关联工单实际确认</span></td><td><template v-if="primaryTicketFor(item)"><RouterLink class="ticket-id" :to="`/tickets/${primaryTicketFor(item)}`">{{ primaryTicketFor(item) }}</RouterLink><span v-if="relatedTicketCount(item) > 1" class="table-subtext">等 {{ relatedTicketCount(item) }} 张</span></template><span v-else class="table-subtext">暂无可授权关联</span></td><td><div class="trusted-link-actions"><button class="button button--secondary" type="button" :disabled="Boolean(openingLinkKey) || !primaryTicketFor(item)" @click="openTrustedLink(primaryTicketFor(item), item, 'LOG')">{{ openingLinkKey === `${primaryTicketFor(item)}-${item.id}-LOG` ? '生成中…' : '查看日志' }}</button><button class="button button--secondary" type="button" :disabled="Boolean(openingLinkKey) || !primaryTicketFor(item)" @click="openTrustedLink(primaryTicketFor(item), item, 'APM')">{{ openingLinkKey === `${primaryTicketFor(item)}-${item.id}-APM` ? '生成中…' : '查看 APM' }}</button></div></td></tr></tbody></table></div>
      <div v-else-if="!loading" class="compact-empty"><div class="empty-icon">□</div><h3>未配置或暂无可见 CI</h3><p>CMDB 未接入、当前 IAM 数据范围无 CI，或同步尚未完成时，工单仍可按服务目录人工创建和处理。</p></div>
      <p v-if="linkError" class="form-alert form-alert--error integration-link-error">{{ linkError }}</p>
    </section>

    <section v-if="Object.keys(issuedLinks).length" class="panel integration-section trusted-link-panel"><div class="panel-header"><div><h3>已生成的受信任诊断链接</h3><p>由服务端基于关联工单和受管集成配置生成；不在页面保存密钥、固定外部地址或查询参数。</p></div><span class="readonly-badge">重新鉴权</span></div>
      <div class="trusted-link-card-list"><article v-for="(link, key) in issuedLinks" :key="key" class="trusted-link-card"><div><b>{{ link.displayName }}</b><span>{{ link.systemCode }} · {{ link.resourceType }} · {{ link.resourceId }}</span></div><a v-if="link.safeUrl" class="button button--secondary" :href="link.safeUrl" target="_blank" rel="noopener noreferrer">在新标签打开</a></article></div>
    </section>

    <section class="panel table-panel integration-section"><div class="panel-header"><div><h3>连接健康与同步状态</h3><p>仅显示服务端脱敏的运行状态；“未配置”是明确状态，不是连接失败。</p></div><span class="readonly-badge">受管配置</span></div>
      <div v-if="overview.connectionHealths.length" class="table-scroll"><table><thead><tr><th>集成</th><th>状态</th><th>启用</th><th>超时预算</th><th>限流摘要</th><th>最近成功</th></tr></thead><tbody><tr v-for="connection in overview.connectionHealths" :key="connection.code"><td><b>{{ systemLabels[connection.systemType] }}</b><span class="table-subtext mono-text">{{ connection.code }}</span></td><td><span class="integration-health" :class="healthClass(connection.healthStatus)">{{ healthLabels[connection.healthStatus] }}</span></td><td>{{ connection.enabled ? '已启用' : '未启用' }}</td><td>{{ connection.timeoutMs ? `${connection.timeoutMs} ms` : '未配置' }}</td><td>{{ connection.rateLimitPerMinute ? `${connection.rateLimitPerMinute} 次/分钟` : '未配置' }}</td><td>{{ formatTime(connection.lastSuccessAt) }}</td></tr></tbody></table></div>
      <div v-else class="compact-empty"><div class="empty-icon">◌</div><h3>暂无集成连接</h3><p>可在受控部署配置中接入 CMDB、监控、日志或 APM；该页面不提供密钥、回调地址或连接参数编辑功能。</p></div>
    </section>
  </template>
</template>
