<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketStatus, type TicketType } from '@/api/tickets'

const route = useRoute()
const ticket = ref<Ticket | null>(null)
const source = ref<'api' | 'demo'>('api')
const loading = ref(true)
const errorMessage = ref('')

const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }
const statusNames: Record<TicketStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起' }
const timeline = computed(() => {
  if (!ticket.value) return []
  const rows = [{ label: '工单已提交', time: ticket.value.createdAt, note: '已记录提交时 IAM 身份快照。' }]
  if (ticket.value.assignee) rows.push({ label: '已进入处理队列', time: ticket.value.updatedAt ?? ticket.value.createdAt, note: `当前处理人：${ticket.value.assignee.displayName}` })
  if (ticket.value.status === 'IN_PROGRESS') rows.push({ label: '处理中', time: ticket.value.updatedAt ?? ticket.value.createdAt, note: '处理进展与动作将由后端事件流返回。' })
  if (ticket.value.status === 'RESOLVED') rows.push({ label: '已解决', time: ticket.value.updatedAt ?? ticket.value.createdAt, note: '等待用户确认或自动关闭。' })
  return rows
})

function formatFullTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) }

async function loadTicket(): Promise<void> {
  const ticketId = String(route.params.ticketId ?? '')
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await ticketApi.get(ticketId)
    ticket.value = result.data
    source.value = result.source
  } catch (error) {
    ticket.value = null
    errorMessage.value = error instanceof ApiError ? error.message : '无法加载此工单，可能已不存在或无权访问。'
  } finally { loading.value = false }
}

onMounted(loadTicket)
watch(() => route.params.ticketId, loadTicket)
</script>

<template>
  <div class="detail-nav"><RouterLink to="/tickets">← 返回我的工单</RouterLink></div>
  <div v-if="loading" class="panel compact-loading">正在加载工单详情…</div>
  <div v-else-if="errorMessage" class="panel empty-state"><span class="empty-icon">!</span><h3>无法打开工单</h3><p>{{ errorMessage }}</p></div>
  <template v-else-if="ticket">
    <div class="page-heading detail-heading"><div><div class="eyebrow">{{ ticket.id }} · {{ typeNames[ticket.type] }}</div><h2>{{ ticket.title }}</h2><div class="tag-row"><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span><span v-for="tag in ticket.tags" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></div></div>
    <p v-if="source === 'demo'" class="demo-notice">演示数据：后端服务不可连接时启用，仅用于界面预览。</p>
    <div class="ticket-detail-layout">
      <section class="panel detail-panel"><div class="panel-header"><div><h3>问题描述</h3><p>提交人填写的结构化信息与补充说明。</p></div></div><p class="ticket-description">{{ ticket.description || '暂无补充说明。' }}</p><dl class="detail-definition"><div><dt>服务目录</dt><dd>{{ ticket.serviceCatalogItem.name }}</dd></div><div><dt>创建时间</dt><dd>{{ formatFullTime(ticket.createdAt) }}</dd></div><div><dt>当前版本</dt><dd>v{{ ticket.version }}（用于后端乐观锁校验）</dd></div></dl></section>
      <aside class="detail-sidebar"><section class="panel detail-panel"><div class="panel-header"><div><h3>处理信息</h3><p>实际动作按钮将依据后端实时授权决定。</p></div></div><dl class="detail-definition"><div><dt>当前状态</dt><dd>{{ statusNames[ticket.status] }}</dd></div><div><dt>当前处理人</dt><dd>{{ ticket.assignee?.displayName ?? '待后端分派' }}</dd></div><div><dt>处理组织</dt><dd>{{ ticket.assignee?.organizationName ?? '—' }}</dd></div></dl></section></aside>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>状态时间线</h3><p>正式环境将以不可篡改审计事件为准。</p></div></div><ol class="ticket-timeline"><li v-for="item in timeline" :key="`${item.label}-${item.time}`"><span></span><div><b>{{ item.label }}</b><small>{{ formatFullTime(item.time) }}</small><p>{{ item.note }}</p></div></li></ol></section>
      <section class="panel detail-panel identity-panel"><div class="panel-header"><div><h3>提交时身份快照</h3><p>保留 IAM ID 与当时组织职位，人员调岗不改写历史。</p></div></div><dl class="detail-definition"><div><dt>姓名</dt><dd>{{ ticket.requester.displayName }}</dd></div><div><dt>IAM 用户 ID</dt><dd class="mono-text">{{ ticket.requester.iamUserId }}</dd></div><div><dt>组织</dt><dd>{{ ticket.requester.organizationName }}</dd></div><div><dt>职位</dt><dd>{{ ticket.requester.positionName ?? '—' }}</dd></div><div><dt>快照时间</dt><dd>{{ formatFullTime(ticket.requester.capturedAt) }}</dd></div></dl></section>
    </div>
  </template>
</template>
