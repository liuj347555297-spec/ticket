<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketQueue, type TicketStatus, type TicketType } from '@/api/tickets'

const tickets = ref<Ticket[]>([])
const total = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const source = ref<'api' | 'demo'>('api')
const filters = ref<{ q: string; status: '' | TicketStatus; type: '' | TicketType; queue: TicketQueue }>({ q: '', status: '', type: '', queue: 'MY_TODO' })
const localFilters = ref({ priority: '', catalog: '', organization: '', startDate: '', endDate: '' })
const showColumnSettings = ref(false)
const exportNotice = ref('')
const visibleColumns = ref<Record<string, boolean>>({ service: true, priority: true, status: true, updated: true })

const typeNames: Record<TicketType, string> = {
  INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请',
}
const statusNames: Record<TicketStatus, string> = {
  DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派',
  PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈',
  CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起',
}
const statusOptions = computed(() => Object.entries(statusNames) as [TicketStatus, string][])
const typeOptions = computed(() => Object.entries(typeNames) as [TicketType, string][])
const queues: Array<{ code: TicketQueue; label: string }> = [
  { code: 'MY_TODO', label: '我的待办' }, { code: 'OVERDUE', label: '逾期待办' }, { code: 'TODAY_COMPLETED', label: '今日完成' },
  { code: 'MY_DONE', label: '我已办' }, { code: 'MY_REQUESTED', label: '我发起的' }, { code: 'DRAFTS', label: '草稿箱' },
  { code: 'TO_READ', label: '我的待阅' }, { code: 'ALL', label: '所有可见工单' },
]
const queueName = computed(() => queues.find((item) => item.code === filters.value.queue)?.label ?? '工单')
const visibleTickets = computed(() => tickets.value.filter((ticket) => {
  const createdAt = ticket.createdAt.slice(0, 10)
  const catalog = `${ticket.serviceCatalogItem.name} ${ticket.type}`.toLowerCase()
  const organization = ticket.requester.organizationName.toLowerCase()
  return (!localFilters.value.priority || ticket.priority === localFilters.value.priority)
    && (!localFilters.value.catalog || catalog.includes(localFilters.value.catalog.trim().toLowerCase()))
    && (!localFilters.value.organization || organization.includes(localFilters.value.organization.trim().toLowerCase()))
    && (!localFilters.value.startDate || createdAt >= localFilters.value.startDate)
    && (!localFilters.value.endDate || createdAt <= localFilters.value.endDate)
}))

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

async function loadTickets(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await ticketApi.list({ page: 1, pageSize: 20, q: filters.value.q.trim() || undefined, status: filters.value.status || undefined, type: filters.value.type || undefined, queue: filters.value.queue })
    tickets.value = result.data.items
    total.value = result.data.total
    source.value = result.source
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : '无法加载工单，请稍后重试。'
    tickets.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetFilters(): void {
  filters.value = { q: '', status: '', type: '', queue: filters.value.queue }
  localFilters.value = { priority: '', catalog: '', organization: '', startDate: '', endDate: '' }
  void loadTickets()
}
function toggleColumn(key: string): void {
  visibleColumns.value[key] = !visibleColumns.value[key]
  localStorage.setItem('servicehub.ticket-list.columns', JSON.stringify(visibleColumns.value))
}
function requestExport(): void {
  exportNotice.value = '导出已安全拦截：生产导出需由服务端按当前数据权限生成、审计并异步下载；当前接口尚未开放。'
}

onMounted(() => {
  try {
    const saved = JSON.parse(localStorage.getItem('servicehub.ticket-list.columns') ?? '{}') as Record<string, boolean>
    visibleColumns.value = { ...visibleColumns.value, ...saved }
  } catch { /* Ignore malformed local display preferences. */ }
  void loadTickets()
})
</script>

<template>
  <div class="page-heading">
    <div><h2>{{ queueName }}</h2><p>队列归属、待办候选资格和数据范围均由服务端按当前 IAM 身份计算。</p></div>
    <RouterLink class="button button--primary" to="/tickets/new">+ 发起工单</RouterLink>
  </div>

  <section class="panel ticket-filter-panel">
    <nav class="ticket-queue-tabs" aria-label="我的流程队列">
      <button v-for="queue in queues" :key="queue.code" type="button" :class="{ 'is-active': filters.queue === queue.code }" @click="filters.queue = queue.code; loadTickets()">{{ queue.label }}</button>
    </nav>
    <form class="ticket-filter" @submit.prevent="loadTickets">
      <label class="field field--grow"><span>关键词</span><input v-model="filters.q" maxlength="100" placeholder="编号、主题、服务或标签" /></label>
      <label class="field"><span>当前状态</span><select v-model="filters.status"><option value="">全部状态</option><option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span>工单类型</span><select v-model="filters.type"><option value="">全部类型</option><option v-for="[value, label] in typeOptions" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span>优先级（当前页）</span><select v-model="localFilters.priority"><option value="">全部优先级</option><option value="P1">P1</option><option value="P2">P2</option><option value="P3">P3</option><option value="P4">P4</option></select></label>
      <label class="field"><span>服务目录（当前页）</span><input v-model.trim="localFilters.catalog" maxlength="100" placeholder="例如 页面卡顿" /></label>
      <label class="field"><span>申请部门（当前页）</span><input v-model.trim="localFilters.organization" maxlength="100" placeholder="组织名称" /></label>
      <label class="field"><span>发起日期（当前页）</span><input v-model="localFilters.startDate" type="date" /></label>
      <label class="field"><span>至</span><input v-model="localFilters.endDate" type="date" /></label>
      <button class="button button--primary" type="submit" :disabled="loading">查询</button>
      <button class="button button--secondary" type="button" @click="resetFilters">重置</button>
      <button class="button button--secondary" type="button" @click="showColumnSettings = !showColumnSettings">列设置</button>
      <button class="button button--secondary" type="button" @click="requestExport">导出</button>
    </form>
    <p class="ticket-filter-hint">关键词、状态、类型和队列由服务端过滤；标注“当前页”的条件仅在已授权返回的本页数据中精筛，不扩大数据范围。</p>
    <div v-if="showColumnSettings" class="column-setting-menu"><b>显示列（仅保存本机展示偏好）</b><label v-for="(enabled, key) in visibleColumns" :key="key"><input type="checkbox" :checked="enabled" @change="toggleColumn(key)" />{{ ({ service: '服务目录', priority: '优先级', status: '状态', updated: '最后更新' } as Record<string, string>)[key] }}</label></div>
  </section>

  <p v-if="source === 'demo'" class="demo-notice">演示数据：后端服务不可连接时启用，仅用于界面预览，不代表当前身份或权限。</p>
  <p v-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>
  <p v-if="exportNotice" class="form-alert form-alert--error">{{ exportNotice }}</p>

  <section class="panel table-panel">
    <div class="panel-header"><div><h3>{{ queueName }}</h3><p>服务端返回 {{ total }} 条；当前页精筛后显示 {{ visibleTickets.length }} 条。工单详情仍由服务端逐对象授权。</p></div></div>
    <div v-if="loading" class="compact-loading">正在加载工单…</div>
    <div v-else-if="visibleTickets.length" class="table-scroll">
      <table><thead><tr><th>编号</th><th>工单主题 / 标签</th><th v-if="visibleColumns.service">服务目录</th><th v-if="visibleColumns.priority">优先级</th><th v-if="visibleColumns.status">状态</th><th v-if="visibleColumns.updated">最后更新</th></tr></thead>
        <tbody><tr v-for="ticket in visibleTickets" :key="ticket.id">
          <td><RouterLink class="ticket-id" :to="`/tickets/${ticket.id}`">{{ ticket.id }}</RouterLink></td>
          <td><RouterLink class="ticket-title" :to="`/tickets/${ticket.id}`">{{ ticket.title }}</RouterLink><div class="tag-row"><span v-for="tag in ticket.tags?.slice(0, 3)" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></td>
          <td v-if="visibleColumns.service"><span>{{ typeNames[ticket.type] }}</span><small class="table-subtext">{{ ticket.serviceCatalogItem.name }}</small></td>
          <td v-if="visibleColumns.priority"><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span></td>
          <td v-if="visibleColumns.status"><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span></td>
          <td v-if="visibleColumns.updated">{{ formatTime(ticket.updatedAt ?? ticket.createdAt) }}</td>
        </tr></tbody>
      </table>
    </div>
    <div v-else class="empty-state compact-empty"><span class="empty-icon">⌕</span><h3>没有匹配的工单</h3><p>请调整筛选条件，或发起新的服务请求。</p></div>
  </section>
</template>
