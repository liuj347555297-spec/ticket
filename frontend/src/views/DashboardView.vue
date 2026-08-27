<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ticketApi, type Ticket, type TicketQueue, type TicketType } from '@/api/tickets'
import { announcementApi, type ServiceAnnouncement } from '@/api/announcements'

const loading = ref(true)
const queueTotals = ref<Record<TicketQueue, number>>({ ALL: 0, MY_TODO: 0, OVERDUE: 0, TODAY_COMPLETED: 0, MY_DONE: 0, MY_REQUESTED: 0, DRAFTS: 0, TO_READ: 0 })
const queueTickets = ref<Ticket[]>([])
const source = ref<'api' | 'demo'>('api')
const announcements = ref<ServiceAnnouncement[]>([])
const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }

const metrics = computed(() => [
  { label: '待我处理', value: queueTotals.value.MY_TODO, note: `${queueTotals.value.OVERDUE} 件已逾期`, tone: 'blue', icon: '◷' },
  { label: '我的待阅', value: queueTotals.value.TO_READ, note: '消息关联工单', tone: 'orange', icon: '⊙' },
  { label: '今日完成', value: queueTotals.value.TODAY_COMPLETED, note: `${queueTotals.value.MY_DONE} 件累计已办`, tone: 'green', icon: '✓' },
  { label: '我发起的', value: queueTotals.value.MY_REQUESTED, note: `${queueTotals.value.DRAFTS} 件草稿`, tone: 'red', icon: '!' },
])

const quickServices = [
  { icon: '▣', title: '页面卡顿', note: '网页、业务系统响应慢', tag: '#性能体验' },
  { icon: '◎', title: '账号与权限', note: '开通、变更、登录异常', tag: '#账号权限' },
  { icon: '⌁', title: '网络与连接', note: '办公网、VPN、访问中断', tag: '#网络服务' },
  { icon: '▤', title: '软件与终端', note: '安装、升级、设备故障', tag: '#终端服务' },
  { icon: '◇', title: '应用系统故障', note: '业务应用不可用或报错', tag: '#应用故障' },
  { icon: '+', title: '更多服务', note: '按服务目录选择事项', tag: '服务目录' },
]

const notices = computed(() => [
  { type: 'SLA', title: queueTotals.value.OVERDUE ? `${queueTotals.value.OVERDUE} 件工单已超 SLA` : '暂无已超 SLA 工单', note: 'SLA 状态由服务端计算', tone: queueTotals.value.OVERDUE ? 'warning' : 'muted' },
  { type: '待阅', title: `${queueTotals.value.TO_READ} 条未读工单提醒`, note: '进入消息中心可查看投递状态', tone: 'blue' },
  { type: '知识', title: '知识库支持目录关联检索', note: '待审核知识和运营统计将在知识运营页处理', tone: 'muted' },
])

async function loadDashboard(): Promise<void> {
  loading.value = true
  try {
    const queues: TicketQueue[] = ['MY_TODO', 'OVERDUE', 'TODAY_COMPLETED', 'MY_DONE', 'MY_REQUESTED', 'DRAFTS', 'TO_READ']
    const responses = await Promise.all(queues.map((queue) => ticketApi.list({ queue, page: 1, pageSize: 6 })))
    responses.forEach((response, index) => { queueTotals.value[queues[index]] = response.data.total })
    queueTickets.value = responses[0].data.items
    source.value = responses[0].source
    try { announcements.value = await announcementApi.list() } catch { announcements.value = [] }
  } finally { loading.value = false }
}
function statusName(ticket: Ticket): string { return ({ SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', PENDING_USER_FEEDBACK: '待用户反馈', RESOLVED: '已解决', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起', DRAFT: '草稿' })[ticket.status] }
function announcementWindow(value: ServiceAnnouncement): string { return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(new Date(value.effectiveUntil)) }
onMounted(loadDashboard)
</script>

<template>
  <section class="workspace-hero">
    <div>
      <p class="workspace-hero__eyebrow">SERVICE WORKBENCH <span>·</span> 内网服务台</p>
      <h2>您好，开始处理今天的服务事项</h2>
      <p class="workspace-hero__description">以工单、待办和知识为中心，快速发起服务、跟进处理进度并关注时效风险。</p>
      <div class="workspace-hero__actions">
        <RouterLink class="button button--primary button--hero" to="/tickets/new">＋ 发起服务请求</RouterLink>
        <RouterLink class="button button--ghost" to="/tickets">查看我的工单</RouterLink>
      </div>
    </div>
    <div class="workspace-hero__summary"><span>当前服务状态</span><strong><i></i>整体平稳</strong><small>1 个重大事件正在协同处置</small></div>
  </section>

  <section class="metric-grid workspace-metrics" aria-label="今日服务指标">
    <article v-for="metric in metrics" :key="metric.label" class="metric-card metric-card--workspace">
      <span class="metric-card__icon" :class="`metric-card__icon--${metric.tone}`">{{ metric.icon }}</span>
      <div><span>{{ metric.label }}</span><strong>{{ loading ? '—' : metric.value }}</strong><small :class="`text-${metric.tone}`">{{ metric.note }}</small></div>
    </article>
  </section>

  <section class="workbench-section">
    <div class="section-heading"><div><h3>快捷服务</h3><p>优先选择标准服务事项，减少描述成本并自动匹配处理流程。</p></div><RouterLink to="/service-config">管理服务目录 →</RouterLink></div>
    <div class="quick-service-grid">
      <RouterLink v-for="service in quickServices" :key="service.title" class="quick-service-card" to="/tickets/new">
        <span class="quick-service-card__icon">{{ service.icon }}</span><div><b>{{ service.title }}</b><small>{{ service.note }}</small><em>{{ service.tag }}</em></div><span class="quick-service-card__arrow">›</span>
      </RouterLink>
    </div>
  </section>

  <section class="service-home-grid">
    <section class="panel service-directory-panel">
      <div class="panel-header"><div><h3>服务目录</h3><p>先选标准事项，再填写最少必要字段；流程和处理组由目录规则决定。</p></div><RouterLink to="/tickets/new">进入目录 →</RouterLink></div>
      <div class="service-directory-tabs"><button class="is-active" type="button">常用服务</button><button type="button">业务系统</button><button type="button">终端与网络</button><button type="button">账号与权限</button></div>
      <div class="service-directory-list"><RouterLink to="/tickets/new"><b>应用与页面体验</b><span>页面卡顿、访问报错、浏览器异常</span></RouterLink><RouterLink to="/tickets/new"><b>账户与授权</b><span>账号开通、权限变更、登录失败</span></RouterLink><RouterLink to="/tickets/new"><b>网络与终端</b><span>办公网、VPN、软件安装、设备故障</span></RouterLink></div>
    </section>
    <aside class="service-home-side">
      <section class="panel home-notice-panel"><div class="panel-header"><div><h3>公告</h3><p>服务窗口与平台提醒</p></div><span>{{ announcements.length }} 条</span></div><ul v-if="announcements.length"><li v-for="item in announcements" :key="item.id"><b><i v-if="item.pinned">置顶</i>{{ item.title }}</b><small>{{ item.body }} · 有效至 {{ announcementWindow(item) }}</small></li></ul><p v-else class="home-data-note">当前阅读范围内暂无有效公告。</p></section>
      <section class="panel home-knowledge-panel"><div class="panel-header"><div><h3>推荐知识</h3><p>从已授权知识库读取</p></div><RouterLink to="/knowledge">查看更多 →</RouterLink></div><RouterLink to="/knowledge">页面加载缓慢的基础排查步骤</RouterLink><RouterLink to="/knowledge">账号权限申请的标准材料</RouterLink><RouterLink to="/knowledge">VPN 连接失败的常见处理</RouterLink></section>
    </aside>
  </section>

  <div class="workspace-content-grid">
    <section class="panel panel--flush work-queue-panel">
      <div class="panel-header panel-header--workspace"><div><h3>我的工作队列</h3><p>待办资格和处理范围由服务端计算</p></div><div class="workspace-tabs"><button class="is-active" type="button">待处理 <b>{{ queueTotals.MY_TODO }}</b></button><button type="button">我发起的 <b>{{ queueTotals.MY_REQUESTED }}</b></button></div></div>
      <div class="table-scroll"><table class="work-queue-table"><thead><tr><th>工单编号 / 服务事项</th><th>处理状态</th><th>处理组</th><th>剩余 SLA</th><th></th></tr></thead><tbody>
        <tr v-for="ticket in queueTickets" :key="ticket.id"><td><RouterLink class="ticket-id" :to="`/tickets/${ticket.id}`">{{ ticket.id }}</RouterLink><b>{{ ticket.title }}</b><span class="tag tag--muted">{{ typeNames[ticket.type] }}</span></td><td><span class="status-pill" :class="ticket.status === 'IN_PROGRESS' ? 'status-pill--in_progress' : ''">{{ statusName(ticket) }}</span><span class="priority-dot" :class="`priority-dot--${ticket.priority.toLowerCase()}`">{{ ticket.priority }}</span></td><td>服务端待办队列</td><td :class="ticket.priority === 'P1' ? 'sla-countdown sla-countdown--danger' : 'sla-countdown'">详情查看</td><td><RouterLink class="table-action" :to="`/tickets/${ticket.id}`">查看</RouterLink></td></tr>
      </tbody></table></div>
      <div class="panel-footer"><span>{{ source === 'demo' ? '演示数据' : `当前显示 ${queueTickets.length} 条，共 ${queueTotals.MY_TODO} 条待处理事项` }}</span><RouterLink to="/tickets">进入工单中心 →</RouterLink></div>
    </section>

    <aside class="workspace-side">
      <section class="panel attention-panel"><div class="panel-header"><div><h3>服务提醒</h3><p>需要优先关注</p></div><RouterLink to="/notifications">全部</RouterLink></div><ul class="attention-list"><li v-for="notice in notices" :key="notice.title"><span :class="`attention-list__type attention-list__type--${notice.tone}`">{{ notice.type }}</span><div><b>{{ notice.title }}</b><small>{{ notice.note }}</small></div></li></ul></section>
      <section class="panel shift-panel"><div><span class="shift-panel__icon">◒</span><div><b>今日值班与协同</b><small>网络运维组 · 白班 08:30 - 17:30</small></div></div><RouterLink to="/operations">查看运行治理 →</RouterLink></section>
    </aside>
  </div>
</template>
