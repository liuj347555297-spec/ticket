<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketPriority, type TicketQuery, type TicketQueue, type TicketStatus, type TicketType } from '@/api/tickets'
import { useSessionStore } from '@/stores/session'
import { defaultTicketColumns, mergeTicketColumns, ticketColumnStorageKey, ticketCsv, ticketQueueFromQuery, type TicketColumnKey } from '@/utils/ticketListWorkspace'
import '@/styles/ticket-list-workspace.css'

interface TicketListFilters { q: string; status: '' | TicketStatus; type: '' | TicketType; priority: '' | TicketPriority; serviceCatalog: string; requesterOrganization: string; createdFrom: string; createdTo: string; queue: TicketQueue }
const route = useRoute(), router = useRouter(), session = useSessionStore()
const tickets = ref<Ticket[]>([]), total = ref(0), loading = ref(false), exporting = ref(false), errorMessage = ref(''), source = ref<'api' | 'demo'>('api')
const filters = ref<TicketListFilters>({ q: '', status: '', type: '', priority: '', serviceCatalog: '', requesterOrganization: '', createdFrom: '', createdTo: '', queue: ticketQueueFromQuery(route.query.queue) })
const pageSize = ref<20 | 50 | 100>(20), currentCursor = ref<string>(), nextCursor = ref<string>(), hasMore = ref(false), snapshotAt = ref<string>(), cursorHistory = ref<Array<string | undefined>>([])
const showColumnSettings = ref(false), showMoreFilters = ref(false), exportNotice = ref('')
const visibleColumns = ref<Record<TicketColumnKey, boolean>>({ ...defaultTicketColumns })
const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }
const statusNames: Record<TicketStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起' }
const statusOptions = computed(() => Object.entries(statusNames) as [TicketStatus, string][]), typeOptions = computed(() => Object.entries(typeNames) as [TicketType, string][])
const queueGroups: Array<{ label: string; items: Array<{ code: TicketQueue; label: string; hint?: string }> }> = [
  { label: '个人工作', items: [{ code: 'MY_TODO', label: '我的待办' }, { code: 'OVERDUE', label: '逾期待办' }, { code: 'TODAY_DUE', label: '当日需完成' }, { code: 'TODAY_COMPLETED', label: '今日完成' }, { code: 'MY_DONE', label: '我的已办' }, { code: 'MY_REQUESTED', label: '我的创建' }, { code: 'DRAFTS', label: '草稿箱' }] },
  { label: '消息与范围', items: [{ code: 'TO_READ', label: '我的待阅', hint: '未读消息关联工单' }, { code: 'ALL', label: '所有可见工单' }] },
]
const allQueues = queueGroups.flatMap(group => group.items), queueName = computed(() => allQueues.find(item => item.code === filters.value.queue)?.label ?? '工单')
const currentPage = computed(() => cursorHistory.value.length + 1), canGoPrevious = computed(() => cursorHistory.value.length > 0 && !loading.value), canGoNext = computed(() => hasMore.value && Boolean(nextCursor.value) && !loading.value)
const columnLabels: Record<TicketColumnKey, string> = { service: '服务目录', requester: '申请人', assignee: '当前处理人', priority: '优先级', status: '当前进度', created: '发起时间', updated: '最后更新' }
const activeFilterCount = computed(() => [filters.value.q, filters.value.status, filters.value.type, filters.value.priority, filters.value.serviceCatalog, filters.value.requesterOrganization, filters.value.createdFrom, filters.value.createdTo].filter(Boolean).length)
let requestGeneration = 0, disposed = false

function formatTime(value: string): string { const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(parsed) }
function queryForCurrentPage(cursor = currentCursor.value, size = pageSize.value): TicketQuery { return { page: cursor ? undefined : 1, pageSize: size, cursor, q: filters.value.q.trim() || undefined, status: filters.value.status || undefined, type: filters.value.type || undefined, priority: filters.value.priority || undefined, queue: filters.value.queue, serviceCatalog: filters.value.serviceCatalog.trim() || undefined, requesterOrganization: filters.value.requesterOrganization.trim() || undefined, createdFrom: filters.value.createdFrom || undefined, createdTo: filters.value.createdTo || undefined } }
function validDateRange(): boolean { if (!filters.value.createdFrom || !filters.value.createdTo || filters.value.createdFrom <= filters.value.createdTo) return true; errorMessage.value = '发起日期的开始时间不能晚于结束时间。'; return false }
async function loadTickets(): Promise<boolean> {
  if (!validDateRange()) return false
  const generation = ++requestGeneration; loading.value = true; errorMessage.value = ''; exportNotice.value = ''
  try { const result = await ticketApi.list(queryForCurrentPage()); if (disposed || generation !== requestGeneration) return false; tickets.value = result.data.items; total.value = result.data.total; nextCursor.value = result.data.nextCursor; hasMore.value = result.data.hasMore ?? Boolean(result.data.nextCursor); snapshotAt.value = result.data.snapshotAt; source.value = result.source; return true }
  catch (error) { if (!disposed && generation === requestGeneration) errorMessage.value = error instanceof ApiError ? error.message : '无法加载工单，请稍后重试。'; return false }
  finally { if (!disposed && generation === requestGeneration) loading.value = false }
}
function resetPagination(clearRows = true): void { currentCursor.value = undefined; nextCursor.value = undefined; hasMore.value = false; snapshotAt.value = undefined; cursorHistory.value = []; if (clearRows) { tickets.value = []; total.value = 0 } }
function applyFilters(): void { resetPagination(); void loadTickets() }
function resetFilters(): void { filters.value = { q: '', status: '', type: '', priority: '', serviceCatalog: '', requesterOrganization: '', createdFrom: '', createdTo: '', queue: filters.value.queue }; showMoreFilters.value = false; applyFilters() }
function changePageSize(): void { resetPagination(); void loadTickets() }
async function goNext(): Promise<void> { if (!canGoNext.value || !nextCursor.value) return; const previousCursor = currentCursor.value, targetCursor = nextCursor.value; cursorHistory.value.push(previousCursor); currentCursor.value = targetCursor; if (!await loadTickets()) { currentCursor.value = previousCursor; cursorHistory.value.pop() } }
async function goPrevious(): Promise<void> { if (!canGoPrevious.value) return; const previousCursor = currentCursor.value, targetCursor = cursorHistory.value.pop(); currentCursor.value = targetCursor; if (!await loadTickets()) { currentCursor.value = previousCursor; cursorHistory.value.push(targetCursor) } }
function persistColumns(): void { const actor = session.currentUser?.iamUserId; if (actor) localStorage.setItem(ticketColumnStorageKey(actor), JSON.stringify(visibleColumns.value)) }
function toggleColumn(key: TicketColumnKey): void { visibleColumns.value[key] = !visibleColumns.value[key]; persistColumns() }
function loadColumns(): void { const actor = session.currentUser?.iamUserId; if (!actor) { visibleColumns.value = { ...defaultTicketColumns }; return }; try { visibleColumns.value = mergeTicketColumns(JSON.parse(localStorage.getItem(ticketColumnStorageKey(actor)) ?? '{}')) } catch { visibleColumns.value = { ...defaultTicketColumns } } }
async function requestExport(): Promise<void> {
  if (exporting.value || loading.value || !validDateRange()) return
  if (total.value > 5000) { exportNotice.value = '当前筛选结果超过 5000 条，请继续收窄条件后导出 CSV。'; return }
  const identity = session.currentUser?.iamUserId, filterSnapshot = JSON.stringify(filters.value); exporting.value = true; exportNotice.value = ''
  try {
    const rows: Ticket[] = []; let cursor: string | undefined; let pages = 0; let resultSource: 'api' | 'demo' = 'api'
    do { const result = await ticketApi.list({ ...queryForCurrentPage(cursor ?? '', 100), page: cursor ? undefined : 1 }); if (identity !== session.currentUser?.iamUserId || filterSnapshot !== JSON.stringify(filters.value)) throw new Error('导出期间身份或筛选条件已变化'); rows.push(...result.data.items); cursor = result.data.nextCursor; resultSource = result.source; pages += 1; if (rows.length > 5000 || pages > 50) throw new Error('导出结果超过 5000 条') } while (cursor)
    const blob = new Blob([ticketCsv(rows, { type: typeNames, status: statusNames })], { type: 'text/csv;charset=utf-8' }), url = URL.createObjectURL(blob), link = document.createElement('a')
    link.href = url; link.download = `工单-${filters.value.queue}-${new Date().toISOString().slice(0, 10)}.csv`; link.click(); URL.revokeObjectURL(url); exportNotice.value = `已导出当前筛选范围 ${rows.length} 条 CSV${resultSource === 'demo' ? '（本地预览数据）' : ''}。`
  } catch (error) { exportNotice.value = error instanceof ApiError ? error.message : error instanceof Error ? error.message : '导出失败。' }
  finally { exporting.value = false }
}

watch(() => route.query.queue, value => { const queue = ticketQueueFromQuery(value); if (queue === 'DRAFTS') { void router.replace('/ticket-drafts'); return }; if (queue !== filters.value.queue) { filters.value.queue = queue; resetPagination(); void loadTickets() } })
watch(() => session.currentUser?.iamUserId, loadColumns)
onMounted(() => { loadColumns(); if (filters.value.queue === 'DRAFTS') void router.replace('/ticket-drafts'); else void loadTickets() })
onBeforeUnmount(() => { disposed = true; requestGeneration += 1 })
</script>

<template>
  <div class="page-heading ticket-list-heading"><div><h2>我的流程</h2><p>{{ queueName }}<span v-if="activeFilterCount"> · 已设置 {{ activeFilterCount }} 个筛选条件</span></p></div><RouterLink class="button button--primary" to="/tickets/new">+ 发起工单</RouterLink></div>
  <div class="my-flow-layout"><main class="flow-list-main">
      <section class="panel ticket-filter-panel ticket-filter-panel--compact">
        <form class="ticket-filter ticket-filter--primary" @submit.prevent="applyFilters"><el-input v-model="filters.q" clearable maxlength="100" placeholder="工单号、主题或服务目录" aria-label="关键词" /><el-select v-model="filters.type" clearable placeholder="工单类型" aria-label="工单类型"><el-option v-for="[value, label] in typeOptions" :key="value" :value="value" :label="label" /></el-select><el-select v-model="filters.status" clearable placeholder="当前进度" aria-label="当前进度"><el-option v-for="[value, label] in statusOptions" :key="value" :value="value" :label="label" /></el-select><el-select v-model="filters.priority" clearable placeholder="优先级" aria-label="优先级"><el-option v-for="priority in ['P1','P2','P3','P4']" :key="priority" :value="priority" :label="priority" /></el-select><el-button type="primary" native-type="submit" :loading="loading">查询</el-button><el-button :disabled="loading" @click="resetFilters">重置</el-button></form>
        <div class="ticket-filter-tools"><el-button link type="primary" @click="showMoreFilters = !showMoreFilters">{{ showMoreFilters ? '收起筛选 ↑' : '更多筛选 ↓' }}</el-button><el-popover v-model:visible="showColumnSettings" placement="bottom-end" trigger="click" :width="240"><template #reference><el-button>列设置</el-button></template><div class="column-setting-menu"><b>显示列</b><el-checkbox v-for="(enabled, key) in visibleColumns" :key="key" :model-value="enabled" @change="toggleColumn(key)">{{ columnLabels[key] }}</el-checkbox><small>已按当前账号保存在本机。</small></div></el-popover><el-button :loading="exporting" :disabled="loading" @click="requestExport">导出 CSV</el-button></div>
        <div v-if="showMoreFilters" class="ticket-filter ticket-filter--more"><el-input v-model="filters.serviceCatalog" clearable maxlength="100" placeholder="服务目录" /><el-input v-model="filters.requesterOrganization" clearable maxlength="100" placeholder="申请部门" /><label><span>发起日期</span><input v-model="filters.createdFrom" type="date" /></label><label><span>至</span><input v-model="filters.createdTo" type="date" /></label></div>
      </section>
      <el-alert v-if="source === 'demo'" type="warning" :closable="false" show-icon title="当前为本地界面预览数据" /><el-alert v-if="errorMessage" type="error" :closable="false" show-icon>{{ errorMessage }}</el-alert><el-alert v-if="exportNotice" :type="exportNotice.startsWith('已导出') ? 'success' : 'warning'" :closable="false" show-icon>{{ exportNotice }}</el-alert>
      <section class="panel table-panel ticket-result-panel">
        <header><div><h3>{{ queueName }}</h3><p>共 {{ total }} 条，当前第 {{ currentPage }} 页</p></div><span>按发起时间倒序</span></header>
        <div class="ticket-result-scroll"><table aria-label="工单列表"><thead><tr><th>工单编号</th><th>主题</th><th v-if="visibleColumns.service">服务目录</th><th v-if="visibleColumns.requester">申请人</th><th v-if="visibleColumns.assignee">当前处理人</th><th v-if="visibleColumns.priority">优先级</th><th v-if="visibleColumns.status">当前进度</th><th v-if="visibleColumns.created">发起时间</th><th v-if="visibleColumns.updated">最后更新</th><th>操作</th></tr></thead><tbody><tr v-for="ticket in tickets" :key="ticket.id"><td><RouterLink class="ticket-id" :to="`/tickets/${ticket.id}`">{{ ticket.id }}</RouterLink></td><td><RouterLink class="ticket-title" :to="`/tickets/${ticket.id}`">{{ ticket.title }}</RouterLink></td><td v-if="visibleColumns.service"><span>{{ ticket.serviceCatalogItem.name }}</span><small class="table-subtext">{{ typeNames[ticket.type] }}</small></td><td v-if="visibleColumns.requester">{{ ticket.requester.displayName }}</td><td v-if="visibleColumns.assignee">{{ ticket.assignee?.displayName ?? '待受理' }}</td><td v-if="visibleColumns.priority"><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span></td><td v-if="visibleColumns.status"><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span></td><td v-if="visibleColumns.created">{{ formatTime(ticket.createdAt) }}</td><td v-if="visibleColumns.updated">{{ formatTime(ticket.updatedAt ?? ticket.createdAt) }}</td><td><RouterLink class="table-action" :to="`/tickets/${ticket.id}`">查看</RouterLink></td></tr></tbody></table></div>
        <div v-if="loading" class="compact-loading" role="status">正在加载工单…</div><div v-else-if="!errorMessage && !tickets.length" class="empty-state compact-empty"><span class="empty-icon">⌕</span><h3>暂无数据</h3><p>当前队列和筛选条件下没有工单。</p></div>
        <footer class="ticket-pagination" aria-label="工单列表分页"><div class="ticket-pagination__summary"><span>共 {{ total }} 条</span><small>查询时间：{{ snapshotAt ? formatTime(snapshotAt) : '—' }}</small></div><div class="ticket-pagination__controls"><label>每页<select v-model.number="pageSize" :disabled="loading" @change="changePageSize"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label><el-button :disabled="!canGoPrevious" @click="goPrevious">上一页</el-button><b>{{ currentPage }}</b><el-button type="primary" plain :disabled="!canGoNext" @click="goNext">下一页</el-button></div></footer>
      </section>
  </main></div>
</template>
