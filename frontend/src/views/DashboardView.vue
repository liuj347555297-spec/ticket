<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ticketApi, type Ticket, type TicketQueue, type TicketType } from '@/api/tickets'
import { announcementApi, type ServiceAnnouncement } from '@/api/announcements'
import { useSessionStore } from '@/stores/session'
import ServiceSystemPortal from '@/components/ServiceSystemPortal.vue'
import '@/styles/dashboard-interactions.css'
import '@/styles/system-home-portal.css'

type DashboardState = 'LOADING' | 'READY' | 'EMPTY' | 'ERROR' | 'PARTIAL'
type SectionState = 'IDLE' | 'LOADING' | 'READY' | 'ERROR'
const queues = ['MY_TODO', 'OVERDUE', 'TODAY_COMPLETED', 'MY_DONE', 'MY_REQUESTED', 'DRAFTS', 'TO_READ'] as const satisfies readonly TicketQueue[]
type DashboardQueue = typeof queues[number]
type VisibleQueue = 'MY_TODO' | 'MY_REQUESTED'
interface QueueSection { state: SectionState; total: number; items: Ticket[]; source: 'api' | 'demo' | null }
function emptyQueues(): Record<DashboardQueue, QueueSection> {
  const empty = (): QueueSection => ({ state: 'IDLE', total: 0, items: [], source: null })
  return { MY_TODO: empty(), OVERDUE: empty(), TODAY_COMPLETED: empty(), MY_DONE: empty(), MY_REQUESTED: empty(), DRAFTS: empty(), TO_READ: empty() }
}
const session = useSessionStore()
const queueSections = ref(emptyQueues())
const activeQueue = ref<VisibleQueue>('MY_TODO')
const activeSection = computed(() => queueSections.value[activeQueue.value])
const activeQueueLabel = computed(() => activeQueue.value === 'MY_TODO' ? '待处理' : '我发起的')
const visibleQueues: Array<{ code: VisibleQueue; label: string }> = [{ code: 'MY_TODO', label: '待处理' }, { code: 'MY_REQUESTED', label: '我发起的' }]
const source = computed(() => queues.some((queue) => queueSections.value[queue].source === 'demo') ? 'demo' : null)
const announcements = ref<ServiceAnnouncement[]>([])
const announcementState = ref<SectionState>('IDLE')
const lastUpdatedAt = ref<Date | null>(null)
const identityReady = computed(() => Boolean(session.currentUser) && !session.loading)
const sectionStates = computed(() => [...queues.map((queue) => queueSections.value[queue].state), announcementState.value])
const loading = computed(() => sectionStates.value.some((status) => status === 'LOADING' || status === 'IDLE'))
const failedSections = computed(() => sectionStates.value.filter((status) => status === 'ERROR').length)
const state = computed<DashboardState>(() => {
  if (loading.value) return 'LOADING'
  if (sectionStates.value.every((status) => status === 'ERROR')) return 'ERROR'
  if (failedSections.value) return 'PARTIAL'
  return queues.every((queue) => queueSections.value[queue].total === 0) && announcements.value.length === 0 ? 'EMPTY' : 'READY'
})
const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }

function queueAvailable(queue: DashboardQueue): boolean { return queueSections.value[queue].state === 'READY' }
function queueTotal(queue: DashboardQueue): number { return queueSections.value[queue].total }
function queuePending(queue: DashboardQueue): boolean { return ['IDLE', 'LOADING'].includes(queueSections.value[queue].state) }
function queueCount(queue: DashboardQueue): number | string { return queueAvailable(queue) ? queueTotal(queue) : '—' }
function queueUnavailableText(queue: DashboardQueue): string { return queuePending(queue) ? '正在加载' : '暂不可用' }
const metrics = computed(() => [
  { label: '待我处理', value: queueCount('MY_TODO'), note: queueAvailable('OVERDUE') ? `${queueTotal('OVERDUE')} 件已逾期` : `逾期统计${queueUnavailableText('OVERDUE')}`, available: queueAvailable('MY_TODO'), tone: 'blue', icon: '◷' },
  { label: '我的待阅', value: queueCount('TO_READ'), note: queueAvailable('TO_READ') ? '消息关联工单' : `待阅统计${queueUnavailableText('TO_READ')}`, available: queueAvailable('TO_READ'), tone: 'orange', icon: '⊙' },
  { label: '今日完成', value: queueCount('TODAY_COMPLETED'), note: queueAvailable('MY_DONE') ? `${queueTotal('MY_DONE')} 件累计已办` : `累计统计${queueUnavailableText('MY_DONE')}`, available: queueAvailable('TODAY_COMPLETED'), tone: 'green', icon: '✓' },
  { label: '我发起的', value: queueCount('MY_REQUESTED'), note: queueAvailable('DRAFTS') ? `${queueTotal('DRAFTS')} 件草稿` : `草稿统计${queueUnavailableText('DRAFTS')}`, available: queueAvailable('MY_REQUESTED'), tone: 'red', icon: '!' },
])

const notices = computed(() => [
  { type: 'SLA', title: queueAvailable('OVERDUE') ? (queueTotal('OVERDUE') ? `${queueTotal('OVERDUE')} 件工单已超 SLA` : '当前范围暂无已超 SLA 工单') : `SLA 数据${queueUnavailableText('OVERDUE')}`, note: 'SLA 状态由服务端计算', tone: queueTotal('OVERDUE') ? 'warning' : 'muted' },
  { type: '待阅', title: queueAvailable('TO_READ') ? `${queueTotal('TO_READ')} 条未读工单提醒` : `待阅数据${queueUnavailableText('TO_READ')}`, note: '进入消息中心可查看投递状态', tone: 'blue' },
  { type: '知识', title: '知识库支持目录关联检索', note: '待审核知识和运营统计将在知识运营页处理', tone: 'muted' },
])

const statePresentation = computed(() => {
  if (session.loading) return { title: '正在确认当前身份', detail: '身份确认后加载可见范围内的数据' }
  if (!identityReady.value) return { title: '当前身份信息不可用', detail: '请重新登录或刷新页面确认身份后再试' }
  if (state.value === 'LOADING') return { title: '正在加载工作台', detail: lastUpdatedAt.value ? '正在读取其余区域，已成功的数据保持不变' : '各区域独立加载，成功后即可查看' }
  if (state.value === 'ERROR') return { title: '工作台暂时不可用', detail: '未能读取服务端数据，请稍后重试' }
  if (state.value === 'PARTIAL') return { title: '部分数据暂不可用', detail: '可用区域已展示，失败区域不会以零值代替' }
  if (state.value === 'EMPTY') return { title: '当前范围暂无事项', detail: '服务端查询成功，暂时没有需要展示的数据' }
  return { title: '工作台数据已更新', detail: lastUpdatedAt.value ? `最近一次成功读取 ${lastUpdatedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}；各区域独立查询` : '数据来自当前服务端授权范围' }
})

let requestGeneration = 0
let disposed = false
async function loadSections(selectedQueues: readonly DashboardQueue[], includeAnnouncements: boolean): Promise<void> {
  const generation = requestGeneration
  const isCurrent = () => !disposed && generation === requestGeneration && identityReady.value
  if (!isCurrent()) return
  selectedQueues.forEach((queue) => { queueSections.value[queue].state = 'LOADING' })
  if (includeAnnouncements) announcementState.value = 'LOADING'
  const requests = selectedQueues.map(async (queue) => {
    try {
      const result = await ticketApi.list({ queue, page: 1, pageSize: 6 })
      if (!isCurrent()) return
      queueSections.value[queue] = { state: 'READY', total: result.data.total, items: result.data.items, source: result.source }
      lastUpdatedAt.value = new Date()
    } catch {
      if (isCurrent()) queueSections.value[queue].state = 'ERROR'
    }
  })
  if (includeAnnouncements) requests.push((async () => {
    try {
      const result = await announcementApi.list()
      if (!isCurrent()) return
      announcements.value = result
      announcementState.value = 'READY'
      lastUpdatedAt.value = new Date()
    } catch {
      if (isCurrent()) announcementState.value = 'ERROR'
    }
  })())
  await Promise.all(requests)
}
function retryFailed(): void {
  // Set loading synchronously inside loadSections so rapid clicks cannot start another retry.
  if (loading.value || !identityReady.value) return
  void loadSections(queues.filter((queue) => queueSections.value[queue].state === 'ERROR'), announcementState.value === 'ERROR')
}
function statusName(ticket: Ticket): string { return ({ SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', PENDING_USER_FEEDBACK: '待用户反馈', RESOLVED: '已解决', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起', DRAFT: '草稿' })[ticket.status] }
function announcementWindow(value: ServiceAnnouncement): string { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value.effectiveUntil)) }
// Identity and scope changes clear the visible projection before any earlier response can write back.
watch(() => JSON.stringify([session.loading, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId, session.source, session.authorization]), () => {
  requestGeneration += 1
  queueSections.value = emptyQueues()
  announcements.value = []
  lastUpdatedAt.value = null
  activeQueue.value = 'MY_TODO'
  announcementState.value = 'IDLE'
  if (identityReady.value) void loadSections(queues, true)
  else if (!session.loading) {
    queues.forEach((queue) => { queueSections.value[queue].state = 'ERROR' })
    announcementState.value = 'ERROR'
  }
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { disposed = true; requestGeneration += 1 })
</script>

<template>
  <section class="portal-home-header">
    <div>
      <h2>服务工作台</h2>
      <p>从业务系统发起工单，集中跟进您的服务事项。</p>
    </div>
    <div class="portal-home-header__status" role="status" aria-live="polite">
      <strong>{{ statePresentation.title }}</strong>
      <small>{{ statePresentation.detail }}</small>
      <small v-if="source === 'demo'">当前为本地演示数据，不代表实时运营状态。</small>
      <div v-if="failedSections && identityReady" class="dashboard-state__actions"><button type="button" :disabled="loading" @click="retryFailed">{{ loading ? '正在重试…' : '重试失败项' }}</button></div>
    </div>
  </section>

  <section class="metric-grid portal-home-metrics" aria-label="今日服务指标">
    <article v-for="metric in metrics" :key="metric.label" class="metric-card metric-card--workspace" :class="{ 'metric-card--unavailable': !metric.available }">
      <span class="metric-card__icon" :class="`metric-card__icon--${metric.tone}`">{{ metric.icon }}</span>
      <div><span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small :class="`text-${metric.tone}`">{{ metric.note }}</small></div>
    </article>
  </section>

  <p v-if="state === 'ERROR'" class="dashboard-status-message dashboard-status-message--error">{{ identityReady ? '工作台数据加载失败，未使用缓存数字或默认零值。' : '身份信息不可用，未读取工作台数据。请重新登录或刷新页面。' }}<button v-if="identityReady" type="button" :disabled="loading" @click="retryFailed">重试失败项</button></p>
  <p v-else-if="state === 'PARTIAL'" class="dashboard-status-message">{{ failedSections }} 个区域暂不可用；页面保留成功返回的数据。<button type="button" :disabled="loading" @click="retryFailed">重试失败项</button></p>
  <p v-else-if="state === 'LOADING'" class="dashboard-status-message" role="status">{{ lastUpdatedAt ? '仍有区域正在加载，已成功的数据可以继续查看。' : '正在加载各区域的数据，请稍候。' }}</p>
  <p v-else-if="state === 'EMPTY'" class="dashboard-status-message">当前身份可见范围内暂无待办、提醒或公告。</p>

  <section class="portal-home-grid">
    <ServiceSystemPortal />
    <aside class="service-home-side">
      <section class="panel home-notice-panel" :aria-busy="announcementState === 'LOADING' || announcementState === 'IDLE'"><div class="panel-header"><div><h3>公告</h3><p>服务窗口与平台提醒</p></div><span>{{ announcementState === 'READY' ? `${announcements.length} 条` : announcementState === 'ERROR' ? '暂不可用' : '加载中…' }}</span></div><ul v-if="announcementState === 'READY' && announcements.length"><li v-for="item in announcements" :key="item.id"><b><i v-if="item.pinned">置顶</i>{{ item.title }}</b><small>{{ item.body }} · 有效至 {{ announcementWindow(item) }}</small></li></ul><p v-else class="home-data-note" role="status">{{ announcementState === 'READY' ? '当前阅读范围内暂无有效公告。' : announcementState === 'ERROR' ? '公告服务暂时不可用，请重试失败项。' : '正在读取当前可见的公告…' }}</p></section>
      <section class="panel home-knowledge-panel"><div class="panel-header"><div><h3>知识检索入口</h3><p>进入知识库后按当前权限返回结果</p></div><RouterLink to="/knowledge">进入知识库 →</RouterLink></div><p class="home-data-note">可尝试搜索“页面加载缓慢”“账号权限申请”或“VPN 连接失败”。</p></section>
    </aside>
  </section>

  <div class="workspace-content-grid">
    <section class="panel panel--flush work-queue-panel">
      <div class="panel-header panel-header--workspace"><div><h3>我的工作队列</h3><p>待办资格和处理范围由服务端计算</p></div><nav class="workspace-tabs dashboard-queue-switcher" aria-label="切换工作队列"><button v-for="queue in visibleQueues" :key="queue.code" :class="{ 'is-active': activeQueue === queue.code }" type="button" :aria-pressed="activeQueue === queue.code" aria-controls="dashboard-queue-content" @click="activeQueue = queue.code">{{ queue.label }} <b>{{ queueCount(queue.code) }}</b></button></nav></div>
      <div id="dashboard-queue-content" :aria-busy="queuePending(activeQueue)" :aria-label="`${activeQueueLabel}队列`">
        <div v-if="activeSection.state === 'READY' && activeSection.items.length" class="table-scroll"><table class="work-queue-table"><thead><tr><th>工单编号 / 服务事项</th><th>处理状态</th><th>当前处理人</th><th>SLA 详情</th><th></th></tr></thead><tbody>
          <tr v-for="ticket in activeSection.items" :key="ticket.id"><td><RouterLink class="ticket-id" :to="`/tickets/${ticket.id}`">{{ ticket.id }}</RouterLink><b>{{ ticket.title }}</b><span class="tag tag--muted">{{ typeNames[ticket.type] }}</span></td><td><span class="status-pill" :class="ticket.status === 'IN_PROGRESS' ? 'status-pill--in_progress' : ''">{{ statusName(ticket) }}</span><span class="priority-dot" :class="`priority-dot--${ticket.priority.toLowerCase()}`">{{ ticket.priority }}</span></td><td>{{ ticket.assignee?.displayName ?? '未返回处理人' }}</td><td class="sla-countdown"><RouterLink :to="`/tickets/${ticket.id}`">详情查看</RouterLink></td><td><RouterLink class="table-action" :to="`/tickets/${ticket.id}`">查看</RouterLink></td></tr>
        </tbody></table></div>
        <div v-else-if="queuePending(activeQueue)" class="work-queue-empty" role="status"><b>正在加载{{ activeQueueLabel }}队列…</b><small>正在查询当前身份可见的工单，请稍候。</small></div>
        <div v-else-if="activeSection.state === 'ERROR'" class="work-queue-empty" role="status"><b>{{ activeQueueLabel }}队列暂不可用</b><small>查询未成功，请重试；其他已加载的数据保持不变。</small><button v-if="identityReady" class="button button--secondary dashboard-inline-retry" type="button" :disabled="loading" @click="retryFailed">{{ loading ? '正在加载…' : '重试失败项' }}</button></div>
        <div v-else class="work-queue-empty" role="status"><b>{{ activeQueue === 'MY_TODO' ? '当前没有待处理事项' : '当前没有我发起的工单' }}</b><small>服务端查询成功，当前范围没有返回工单。</small></div>
      </div>
      <div class="panel-footer"><span>{{ activeSection.state === 'READY' ? `${activeSection.source === 'demo' ? '本地演示数据 · ' : ''}当前显示 ${activeSection.items.length} 条，共 ${activeSection.total} 条${activeQueueLabel}事项` : queuePending(activeQueue) ? `${activeQueueLabel}数据加载中…` : `${activeQueueLabel}数据不可用` }}</span><RouterLink to="/tickets">进入工单中心 →</RouterLink></div>
    </section>

    <aside class="workspace-side">
      <section class="panel attention-panel"><div class="panel-header"><div><h3>服务提醒</h3><p>需要优先关注</p></div><RouterLink to="/notifications">全部</RouterLink></div><ul class="attention-list"><li v-for="notice in notices" :key="notice.title"><span :class="`attention-list__type attention-list__type--${notice.tone}`">{{ notice.type }}</span><div><b>{{ notice.title }}</b><small>{{ notice.note }}</small></div></li></ul></section>
      <section class="panel dashboard-data-note"><b>数据呈现说明</b><small>工作台仅展示服务端按当前身份返回的数据。菜单隐藏是呈现优化，不代表授权判断；访问具体对象时仍由服务端校验。</small><RouterLink to="/operations">查看运行治理 →</RouterLink></section>
    </aside>
  </div>
</template>
