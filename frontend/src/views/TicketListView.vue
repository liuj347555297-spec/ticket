<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketStatus, type TicketType } from '@/api/tickets'

const tickets = ref<Ticket[]>([])
const total = ref(0)
const loading = ref(true)
const errorMessage = ref('')
const source = ref<'api' | 'demo'>('api')
const filters = ref<{ q: string; status: '' | TicketStatus; type: '' | TicketType }>({ q: '', status: '', type: '' })

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

function formatTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

async function loadTickets(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await ticketApi.list({ page: 1, pageSize: 20, q: filters.value.q.trim() || undefined, status: filters.value.status || undefined, type: filters.value.type || undefined })
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

onMounted(loadTickets)
</script>

<template>
  <div class="page-heading">
    <div><h2>我的工单</h2><p>仅展示当前 IAM 身份在服务端数据范围内可见的工单。</p></div>
    <RouterLink class="button button--primary" to="/tickets/new">+ 发起工单</RouterLink>
  </div>

  <section class="panel ticket-filter-panel">
    <form class="ticket-filter" @submit.prevent="loadTickets">
      <label class="field field--grow"><span>关键词</span><input v-model="filters.q" maxlength="100" placeholder="编号、主题、服务或标签" /></label>
      <label class="field"><span>当前状态</span><select v-model="filters.status"><option value="">全部状态</option><option v-for="[value, label] in statusOptions" :key="value" :value="value">{{ label }}</option></select></label>
      <label class="field"><span>工单类型</span><select v-model="filters.type"><option value="">全部类型</option><option v-for="[value, label] in typeOptions" :key="value" :value="value">{{ label }}</option></select></label>
      <button class="button button--primary" type="submit" :disabled="loading">查询</button>
      <button class="button button--secondary" type="button" @click="filters = { q: '', status: '', type: '' }; loadTickets()">重置</button>
    </form>
  </section>

  <p v-if="source === 'demo'" class="demo-notice">演示数据：后端服务不可连接时启用，仅用于界面预览，不代表当前身份或权限。</p>
  <p v-if="errorMessage" class="form-alert form-alert--error">{{ errorMessage }}</p>

  <section class="panel table-panel">
    <div class="panel-header"><div><h3>工单列表</h3><p>共 {{ total }} 条，工单详情仍由服务端逐对象授权。</p></div></div>
    <div v-if="loading" class="compact-loading">正在加载工单…</div>
    <div v-else-if="tickets.length" class="table-scroll">
      <table><thead><tr><th>编号</th><th>工单主题 / 标签</th><th>服务目录</th><th>优先级</th><th>状态</th><th>最后更新</th></tr></thead>
        <tbody><tr v-for="ticket in tickets" :key="ticket.id">
          <td><RouterLink class="ticket-id" :to="`/tickets/${ticket.id}`">{{ ticket.id }}</RouterLink></td>
          <td><RouterLink class="ticket-title" :to="`/tickets/${ticket.id}`">{{ ticket.title }}</RouterLink><div class="tag-row"><span v-for="tag in ticket.tags?.slice(0, 3)" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></td>
          <td><span>{{ typeNames[ticket.type] }}</span><small class="table-subtext">{{ ticket.serviceCatalogItem.name }}</small></td>
          <td><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span></td>
          <td><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span></td>
          <td>{{ formatTime(ticket.updatedAt ?? ticket.createdAt) }}</td>
        </tr></tbody>
      </table>
    </div>
    <div v-else class="empty-state compact-empty"><span class="empty-icon">⌕</span><h3>没有匹配的工单</h3><p>请调整筛选条件，或发起新的服务请求。</p></div>
  </section>
</template>
