<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type AttachmentScanState, type ControlledJumpPreflight, type Ticket, type TicketActionCode, type TicketAttachment, type TicketAvailableAction, type TicketComment, type TicketLifecycleAction, type TicketRelation, type TicketRelationType, type TicketSla, type TicketSlaResponse, type TicketStatus, type TicketTimelineEvent, type TicketType, type TicketWorkAction } from '@/api/tickets'
import { notificationApi, type TicketNotificationSummary } from '@/api/notifications'
import '@/styles/detail-workflow.css'

const route = useRoute()
const ticket = ref<Ticket | null>(null)
const source = ref<'api' | 'demo'>('api')
const loading = ref(true)
const errorMessage = ref('')
const comments = ref<TicketComment[]>([])
const selectedAction = ref<TicketAvailableAction | null>(null)
const actionSubmitting = ref(false)
const actionNotice = ref('')
const actionError = ref('')
const actionForm = ref({ targetIamUserId: '', reason: '', detail: '' })
const notificationSummary = ref<TicketNotificationSummary | null>(null)
const attachments = ref<TicketAttachment[]>([])
const workflowTask = ref<{ nodeKey: string; status: string; candidateRole?: string; candidateIamUserId?: string; assigneeIamUserId?: string } | null>(null)
const workflowParticipants = ref<{ role: 'PRIMARY' | 'COLLABORATOR'; identity: import('@/api/tickets').IdentitySnapshot; assignedAt: string }[]>([])
const approvalRequests = ref<{ id: string; applicantIamUserId: string; sourceNode: string; targetNode: string; reason: string; status: string; createdAt: string; approverIamUserId?: string; decidedAt?: string; executorIamUserId?: string; executionStartedAt?: string; executedAt?: string; executedFromNode?: string; executedToNode?: string; executionFailureReason?: string; approvalPolicy?: { processKey: string; processDefinitionId: string; processVersion: number; candidateRoles: string[]; decisionMode: string; timeoutPolicyVersion: string; escalationPolicyVersion: string; capturedAt: string } }[]>([])
const approvalDecisions = ref<{ id: string; approvalRequestId: string; engineTaskId?: string; approverIamUserId: string; decision: 'APPROVED' | 'REJECTED'; reason: string; decidedAt: string }[]>([])
const controlledJumpActions = ref<{ requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }[]>([])
const selectedControlledJump = ref<{ requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string } | null>(null)
const controlledJumpPreflight = ref<ControlledJumpPreflight | null>(null)
const controlledJumpSubmitting = ref(false)
const controlledJumpError = ref('')
const relations = ref<TicketRelation[]>([])
const showRelation = ref(false)
const relationSubmitting = ref(false)
const relationError = ref('')
const relationForm = ref<{ targetTicketId: string; relationType: TicketRelationType }>({ targetTicketId: '', relationType: 'RELATED' })

const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }
const statusNames: Record<TicketStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起' }
const actionLabels: Record<TicketActionCode, string> = {
  CLASSIFY: '分类', ASSIGN: '分派', ACCEPT: '受理', REQUEST_USER_FEEDBACK: '待用户反馈', RESOLVE: '解决', CLOSE: '关闭', REOPEN: '重开', CANCEL: '撤销', HOLD: '挂起', RESUME: '恢复', ESCALATE: '升级',
  TRANSFER: '转办', ADD_COHANDLER: '添加协办', CLAIM: '抢单', HANDOVER: '交接班', INTERNAL_COMMENT: '内部评论', CONTROLLED_JUMP_REQUEST: '受控跳转申请',
}
const lifecycleCodes: TicketLifecycleAction[] = ['CLASSIFY', 'ASSIGN', 'ACCEPT', 'REQUEST_USER_FEEDBACK', 'RESOLVE', 'CLOSE', 'REOPEN', 'CANCEL', 'HOLD', 'RESUME', 'ESCALATE']
const targetRequiredCodes: TicketWorkAction[] = ['TRANSFER', 'ADD_COHANDLER', 'HANDOVER']
const demoComments: TicketComment[] = [{ id: 'demo-comment-001', visibility: 'INTERNAL', author: { iamUserId: 'iam-u-000063', displayName: '李工', organizationName: '数字化运营中心 / 应用运维组', positionName: '应用运维工程师', capturedAt: '2026-08-19T09:28:00+08:00' }, content: '已关联性能监控检查项，待确认慢查询与缓存命中情况。', createdAt: '2026-08-19T09:30:00+08:00', auditEventId: 'AUD-20260819-004' }]

const availableActions = computed(() => ticket.value?.availableActions ?? [])
const lifecycleActions = computed(() => availableActions.value.filter((item) => lifecycleCodes.includes(item.code as TicketLifecycleAction)))
const workActions = computed(() => availableActions.value.filter((item) => !lifecycleCodes.includes(item.code as TicketLifecycleAction) && item.code !== 'INTERNAL_COMMENT'))
const canComment = computed(() => availableActions.value.some((item) => item.code === 'INTERNAL_COMMENT'))
const actionTitle = computed(() => selectedAction.value ? selectedAction.value.label ?? actionLabels[selectedAction.value.code] : '')
const needsTarget = computed(() => selectedAction.value?.requiresTarget ?? targetRequiredCodes.includes(selectedAction.value?.code as TicketWorkAction))
const isComment = computed(() => selectedAction.value?.code === 'INTERNAL_COMMENT')
const participants = computed(() => workflowParticipants.value.length ? workflowParticipants.value : ticket.value?.participants?.length ? ticket.value.participants : ticket.value?.assignee ? [{ role: 'PRIMARY' as const, identity: ticket.value.assignee, assignedAt: ticket.value.updatedAt ?? ticket.value.createdAt }] : [])
const timeline = computed<TicketTimelineEvent[]>(() => {
  if (!ticket.value) return []
  if (ticket.value.timeline?.length) return ticket.value.timeline
  const rows = [{ id: 'created', label: '提交工单', occurredAt: ticket.value.createdAt, note: '已记录提交时 IAM 身份快照。', actor: ticket.value.requester }]
  if (ticket.value.assignee) rows.push({ id: 'assigned', label: '进入处理队列', occurredAt: ticket.value.updatedAt ?? ticket.value.createdAt, note: `当前处理人：${ticket.value.assignee.displayName}`, actor: ticket.value.assignee })
  return rows
})
const processNodes = [
  { code: 'classify', label: '分类' }, { code: 'assign', label: '分派' }, { code: 'accept', label: '受理' },
  { code: 'processing', label: '处理' }, { code: 'user_feedback', label: '用户反馈' }, { code: 'closure', label: '关闭' },
]
const relationLabels: Record<TicketRelationType, string> = {
  RELATED: '关联工单', DUPLICATE_OF: '重复于', PARENT_OF: '父工单', PROBLEM_REFERENCE: '关联问题', CHANGE_REFERENCE: '关联变更',
}

function formatFullTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) }
function slaRemaining(minutes: number | undefined): string {
  if (minutes === undefined) return '服务端未返回'
  if (minutes < 0) return `已超时 ${Math.abs(minutes)} 分钟`
  if (minutes === 0) return '已达到目标'
  return minutes < 60 ? `剩余 ${minutes} 分钟` : `剩余 ${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
}
function slaRiskLabel(risk: 'NORMAL' | 'AT_RISK' | 'BREACHED'): string { return risk === 'NORMAL' ? '正常' : risk === 'AT_RISK' ? '临近违约' : '已违约' }
function mapTicketSla(status: TicketSlaResponse): TicketSla {
  if (!('targets' in status)) {
    return {
      policyName: status.policyNameSnapshot,
      responseTargetAt: status.responseDueAt,
      resolutionTargetAt: status.resolutionDueAt,
      paused: Boolean(status.pauseStartedAt),
      pausedAt: status.pauseStartedAt,
      pausedMinutes: Math.floor(status.pausedSeconds / 60),
      riskLevel: status.riskLevel === 'ON_TRACK' ? 'NORMAL' : status.riskLevel,
      calculatedAt: status.calculatedAt,
    }
  }
  const response = status.targets.find((target) => target.targetType === 'FIRST_RESPONSE')
  const resolution = status.targets.find((target) => target.targetType === 'RESOLUTION')
  const states = status.targets.map((target) => target.state)
  const riskLevel = states.includes('BREACHED') ? 'BREACHED' : states.includes('AT_RISK') ? 'AT_RISK' : 'NORMAL'
  return {
    policyName: `${status.policySnapshot.policyId} · v${status.policySnapshot.policyVersion}`,
    responseTargetAt: response?.targetAt,
    resolutionTargetAt: resolution?.targetAt,
    responseRemainingMinutes: response?.businessMinutesRemaining,
    resolutionRemainingMinutes: resolution?.businessMinutesRemaining,
    paused: status.paused,
    riskLevel,
    calculatedAt: status.calculatedAt,
  }
}
function participantRole(role: 'PRIMARY' | 'COLLABORATOR'): string { return role === 'PRIMARY' ? '主办' : '协办' }
const attachmentScanLabel: Record<AttachmentScanState, string> = { RECEIVED: '已接收', SCANNING: '扫描中', SCAN_PASSED: '扫描通过', QUARANTINED: '已隔离', REJECTED: '扫描拒绝', SCAN_UNAVAILABLE: '扫描不可用' }
function attachmentSize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.ceil(bytes / 1024)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB` }
function downloadAttachment(attachmentId: string): void { if (!ticket.value) return; window.location.assign(`${import.meta.env.VITE_API_BASE_URL ?? '/api/v1'}/tickets/${encodeURIComponent(ticket.value.id)}/attachments/${encodeURIComponent(attachmentId)}/content`) }
function actionHelp(action?: TicketActionCode): string {
  if (action === 'INTERNAL_COMMENT') return '仅内部协作人员可见；内容会由服务端净化、脱敏并审计。'
  if (action === 'CLAIM') return '抢单时不指定处理人，服务端会重新校验候选资格。'
  if (action && targetRequiredCodes.includes(action as TicketWorkAction)) return '目标人员只是候选人；服务端会校验其 IAM 投影、班次、技能和数据范围。'
  return '状态、流程节点、审批、SLA 和最终处理人均由服务端规则决定。'
}
function workflowActionLabel(action: string): string {
  const labels: Record<string, string> = {
    WORKFLOW_STARTED: '流程已启动', WORKFLOW_CLASSIFY: '完成分类', WORKFLOW_ASSIGN: '完成分派', WORKFLOW_ACCEPT: '已受理',
    WORKFLOW_REQUEST_USER_FEEDBACK: '请求用户反馈', WORKFLOW_RESOLVE: '已解决', WORKFLOW_CLOSE: '已关闭', WORKFLOW_REOPEN: '已重开',
    WORKFLOW_CANCEL: '已撤销', WORKFLOW_HOLD: '已挂起', WORKFLOW_RESUME: '已恢复', WORKFLOW_ESCALATE: '已升级',
    WORKFLOW_CLAIM: '已抢单', WORKFLOW_TRANSFER: '已转办', WORKFLOW_ADD_COHANDLER: '已添加协办', WORKFLOW_HANDOVER: '已交接班', WORKFLOW_INTERNAL_COMMENT: '新增内部评论', CONTROLLED_JUMP_APPROVED: '受控跳转审批通过', CONTROLLED_JUMP_REJECTED: '受控跳转审批拒绝', CONTROLLED_JUMP_EXECUTED: '受控跳转已执行',
  }
  return labels[action] ?? action
}
function openAction(action: TicketAvailableAction): void { if (!action.disabledReason) { selectedAction.value = action; actionForm.value = { targetIamUserId: '', reason: '', detail: '' }; actionError.value = ''; actionNotice.value = '' } }
function closeAction(): void { if (!actionSubmitting.value) selectedAction.value = null }
function managementActionsForApproval(requestId: string): { requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }[] { return controlledJumpActions.value.filter((item) => item.requestId === requestId) }
function decisionsForApproval(requestId: string): { id: string; approverIamUserId: string; decision: 'APPROVED' | 'REJECTED'; reason: string; decidedAt: string }[] { return approvalDecisions.value.filter((item) => item.approvalRequestId === requestId) }
async function openControlledJump(action: { requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }): Promise<void> {
  if (!ticket.value || source.value === 'demo' || !action.canPreflight) return
  selectedControlledJump.value = action; controlledJumpPreflight.value = null; controlledJumpError.value = ''; controlledJumpSubmitting.value = true
  try { controlledJumpPreflight.value = await ticketApi.preflightControlledJump(ticket.value.id, action.requestId) }
  catch (error) { controlledJumpError.value = error instanceof ApiError ? error.message : '预演失败，请刷新后重试。' }
  finally { controlledJumpSubmitting.value = false }
}
function closeControlledJump(): void { if (!controlledJumpSubmitting.value) selectedControlledJump.value = null }
async function executeControlledJump(): Promise<void> {
  if (!ticket.value || !selectedControlledJump.value || !controlledJumpPreflight.value?.executable) return
  controlledJumpError.value = ''; controlledJumpSubmitting.value = true
  try {
    ticket.value = await ticketApi.executeControlledJump(ticket.value.id, selectedControlledJump.value.requestId, ticket.value.version)
    await Promise.all([loadWorkflow(ticket.value.id, source.value), loadComments(ticket.value.id, source.value), loadSla(ticket.value.id, source.value)])
    selectedControlledJump.value = null
  } catch (error) { controlledJumpError.value = error instanceof ApiError ? error.message : '受控执行失败，请刷新工单后重试。' }
  finally { controlledJumpSubmitting.value = false }
}
function openRelation(): void { relationForm.value = { targetTicketId: '', relationType: 'RELATED' }; relationError.value = ''; showRelation.value = true }
function closeRelation(): void { if (!relationSubmitting.value) showRelation.value = false }
async function submitRelation(): Promise<void> {
  if (!ticket.value || !/^TKT-\d{8}-\d{6}$/.test(relationForm.value.targetTicketId)) { relationError.value = '请填写有效的工单编号。'; return }
  relationError.value = ''; relationSubmitting.value = true
  try {
    await ticketApi.createRelation(ticket.value.id, { targetTicketId: relationForm.value.targetTicketId, relationType: relationForm.value.relationType })
    relations.value = await ticketApi.listRelations(ticket.value.id)
    showRelation.value = false
  } catch (error) { relationError.value = error instanceof ApiError ? error.message : '关联创建失败，请稍后重试。' } finally { relationSubmitting.value = false }
}
async function submitAction(): Promise<void> {
  if (!ticket.value || !selectedAction.value) return
  const action = selectedAction.value.code
  actionError.value = ''; actionNotice.value = ''
  if (!actionForm.value.reason.trim() || (needsTarget.value && !actionForm.value.targetIamUserId.trim()) || (isComment.value && !actionForm.value.detail.trim())) { actionError.value = isComment.value ? '请填写评论目的和评论内容。' : '请填写操作理由及必填候选人员。'; return }
  if (source.value === 'demo') { actionNotice.value = '演示模式已阻断提交：此表单不代表权限校验、审批完成或工单状态变更。'; return }
  actionSubmitting.value = true
  try {
    if (action === 'INTERNAL_COMMENT') {
      await ticketApi.createInternalComment(ticket.value.id, { version: ticket.value.version, reason: actionForm.value.reason.trim(), content: actionForm.value.detail.trim() })
      await Promise.all([loadWorkflow(ticket.value.id, source.value), loadComments(ticket.value.id, source.value)])
      actionNotice.value = '内部评论已由服务端接收并写入审计链。'
    } else {
      const result = await ticketApi.executeAction(ticket.value.id, { action, version: ticket.value.version, reason: actionForm.value.reason.trim(), targetIamUserId: actionForm.value.targetIamUserId.trim() || undefined })
      ticket.value = result.ticket
      await loadWorkflow(ticket.value.id, source.value)
      await loadComments(ticket.value.id, source.value)
      actionNotice.value = result.decision.outcome === 'COMPLETED' ? '服务端已完成动作并返回最新工单。' : '服务端已创建后续审批或流程任务，当前不代表动作已完成。'
    }
  } catch (error) { actionError.value = error instanceof ApiError ? error.message : '动作提交失败，请刷新工单后重试。' } finally { actionSubmitting.value = false }
}
async function loadComments(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> { if (dataSource === 'demo') { comments.value = demoComments; return }; try { comments.value = (await ticketApi.listInternalComments(ticketId)).items } catch { comments.value = [] } }
async function loadWorkflow(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  if (!ticket.value || dataSource === 'demo') return
  try {
    const overview = await ticketApi.getWorkflowOverview(ticketId)
    const activeTask = overview.tasks.find((task) => task.status === 'OPEN' || task.status === 'CLAIMED')
    workflowTask.value = activeTask ?? null
    workflowParticipants.value = overview.participants.map((participant) => ({ ...participant, role: participant.role === 'CO_HANDLER' ? 'COLLABORATOR' : 'PRIMARY' }))
    approvalRequests.value = overview.approvalRequests
    approvalDecisions.value = overview.approvalDecisions ?? []
    controlledJumpActions.value = overview.controlledJumpActions ?? []
    ticket.value = {
      ...ticket.value,
      availableActions: overview.availableActions,
      timeline: overview.events.map((event) => ({
        id: `workflow-${event.id}`, label: workflowActionLabel(event.action), occurredAt: event.occurredAt,
        note: event.requestId === 'system' ? undefined : `请求追踪：${event.requestId}`,
        actor: { iamUserId: event.actorIamUserId, displayName: event.actorIamUserId, organizationName: '受控 IAM 投影', capturedAt: event.occurredAt },
      })),
    }
  } catch {
    // Workflow detail can be independently unavailable. Keeping actions hidden is fail-closed.
    ticket.value = { ...ticket.value, availableActions: [] }; workflowTask.value = null; workflowParticipants.value = []; approvalRequests.value = []; approvalDecisions.value = []; controlledJumpActions.value = []
  }
}
async function loadAttachments(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> { if (dataSource === 'demo') { attachments.value = ticket.value?.attachments ?? []; return }; try { attachments.value = (await ticketApi.listAttachments(ticketId)).items } catch { attachments.value = [] } }
async function loadSla(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  if (!ticket.value || dataSource === 'demo') return
  try { ticket.value = { ...ticket.value, sla: mapTicketSla(await ticketApi.getSla(ticketId)) } } catch { /* SLA may be omitted when unavailable or not separately authorized. */ }
}
async function loadRelations(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  if (dataSource === 'demo') { relations.value = []; return }
  try { relations.value = await ticketApi.listRelations(ticketId) } catch { relations.value = [] }
}
async function loadTicket(): Promise<void> {
  const ticketId = String(route.params.ticketId ?? '')
  loading.value = true; errorMessage.value = ''; selectedAction.value = null; selectedControlledJump.value = null; workflowTask.value = null; workflowParticipants.value = []; approvalRequests.value = []; controlledJumpActions.value = []
  try { const result = await ticketApi.get(ticketId); ticket.value = result.data; source.value = result.source; await Promise.all([loadWorkflow(ticketId, result.source), loadComments(ticketId, result.source), loadAttachments(ticketId, result.source), loadSla(ticketId, result.source), loadRelations(ticketId, result.source)]); try { notificationSummary.value = (await notificationApi.ticketSummary(ticketId)).data } catch { notificationSummary.value = null } } catch (error) { ticket.value = null; comments.value = []; attachments.value = []; relations.value = []; notificationSummary.value = null; errorMessage.value = error instanceof ApiError ? error.message : '无法加载此工单，可能已不存在或无权访问。' } finally { loading.value = false }
}
onMounted(loadTicket)
watch(() => route.params.ticketId, loadTicket)
</script>

<template>
  <div class="detail-nav"><RouterLink to="/tickets">← 返回我的工单</RouterLink></div>
  <div v-if="loading" class="panel compact-loading">正在加载工单详情…</div>
  <div v-else-if="errorMessage" class="panel empty-state"><span class="empty-icon">!</span><h3>无法打开工单</h3><p>{{ errorMessage }}</p></div>
  <template v-else-if="ticket">
    <div class="page-heading detail-heading"><div><div class="eyebrow">{{ ticket.id }} · {{ typeNames[ticket.type] }}</div><h2>{{ ticket.title }}</h2><div class="tag-row"><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span><span v-for="tag in ticket.tags ?? []" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></div></div>
    <p v-if="source === 'demo'" class="demo-notice">演示数据：动作表单仅供预览，已阻断提交；不代表当前身份、权限、审批或状态变更。</p>
    <section v-if="notificationSummary" class="ticket-notification-summary" :class="{ 'ticket-notification-summary--unread': notificationSummary.unreadCount > 0 }"><span aria-hidden="true">♢</span><div><b>{{ notificationSummary.unreadCount ? `本工单有 ${notificationSummary.unreadCount} 条未读通知` : '本工单暂无未读通知' }}</b><small v-if="notificationSummary.latest">最近：{{ notificationSummary.latest.title }} · {{ formatFullTime(notificationSummary.latest.createdAt) }}</small></div><RouterLink to="/notifications">查看消息中心</RouterLink></section>
    <section class="detail-actionbar" aria-label="当前可执行工单操作">
      <div><span>工单操作</span><small>只显示服务端按当前身份、节点和审批规则返回的动作</small></div>
      <div v-if="availableActions.length" class="detail-actionbar__buttons">
        <button v-for="action in lifecycleActions" :key="action.code" class="button button--primary button--compact" type="button" :disabled="Boolean(action.disabledReason)" :title="action.disabledReason" @click="openAction(action)">{{ action.label ?? actionLabels[action.code] }}</button>
        <button v-for="action in workActions" :key="action.code" class="button button--secondary button--compact" type="button" :disabled="Boolean(action.disabledReason)" :title="action.disabledReason" @click="openAction(action)">{{ action.label ?? actionLabels[action.code] }}</button>
        <button v-if="canComment" class="button button--secondary button--compact" type="button" @click="openAction({ code: 'INTERNAL_COMMENT', label: '内部评论' })">内部评论</button>
      </div>
      <small v-else class="detail-actionbar__empty">当前没有可执行操作</small>
    </section>
    <div class="ticket-detail-layout">
      <section class="panel detail-panel"><div class="panel-header"><div><h3>问题描述</h3><p>提交人填写的结构化信息与补充说明。</p></div></div><div v-if="ticket.descriptionFormat === 'RICH_TEXT' && ticket.descriptionHtml" class="ticket-description ticket-description--rich" v-html="ticket.descriptionHtml"></div><p v-else class="ticket-description">{{ ticket.description || '暂无补充说明。' }}</p><dl class="detail-definition"><div><dt>服务目录</dt><dd>{{ ticket.serviceCatalogItem.name }}</dd></div><div><dt>创建时间</dt><dd>{{ formatFullTime(ticket.createdAt) }}</dd></div><div><dt>当前版本</dt><dd>v{{ ticket.version }}（写操作必须校验）</dd></div></dl></section>
      <aside class="detail-sidebar"><section class="panel detail-panel"><div class="panel-header"><div><h3>处理信息</h3><p>处理关系与状态以服务端为准。</p></div></div><dl class="detail-definition"><div><dt>当前状态</dt><dd>{{ statusNames[ticket.status] }}</dd></div><div><dt>当前处理人</dt><dd>{{ ticket.assignee?.displayName ?? '待后端分派' }}</dd></div><div><dt>处理组织</dt><dd>{{ ticket.assignee?.organizationName ?? '—' }}</dd></div></dl></section><section class="panel detail-panel sla-ticket-panel"><div class="panel-header"><div><h3>SLA 时效</h3><p>目标、暂停和风险由服务端计算，前端不自行倒计时。</p></div></div><template v-if="ticket.sla"><div class="sla-ticket-policy"><b>{{ ticket.sla.policyName }}</b><span class="tag" :class="ticket.sla.riskLevel === 'NORMAL' ? 'tag--green' : ticket.sla.riskLevel === 'AT_RISK' ? 'tag--orange' : 'tag--red'">{{ slaRiskLabel(ticket.sla.riskLevel) }}</span></div><dl class="detail-definition"><div><dt>响应目标</dt><dd>{{ ticket.sla.responseTargetAt ? formatFullTime(ticket.sla.responseTargetAt) : '—' }}<small>{{ slaRemaining(ticket.sla.responseRemainingMinutes) }}</small></dd></div><div><dt>解决目标</dt><dd>{{ ticket.sla.resolutionTargetAt ? formatFullTime(ticket.sla.resolutionTargetAt) : '—' }}<small>{{ slaRemaining(ticket.sla.resolutionRemainingMinutes) }}</small></dd></div><div><dt>计时状态</dt><dd>{{ ticket.sla.paused ? '已暂停（已审批）' : '计时中' }}<small v-if="ticket.sla.pausedMinutes">累计暂停 {{ ticket.sla.pausedMinutes }} 分钟</small></dd></div><div><dt>最近计算</dt><dd>{{ formatFullTime(ticket.sla.calculatedAt) }}</dd></div></dl></template><p v-else class="workflow-unavailable">当前未返回可查看的 SLA 明细；工单是否可见及 SLA 数据范围由服务端控制。</p></section></aside>
      <section class="panel detail-panel process-overview-panel"><div class="panel-header"><div><h3>流程与当前任务</h3><p>节点和候选资格均来自服务端工作流读模型。</p></div></div><div class="process-node-strip"><div v-for="node in processNodes" :key="node.code" :class="{ 'is-current': workflowTask?.nodeKey === node.code }"><span>{{ node.label }}</span></div></div><dl v-if="workflowTask" class="detail-definition process-task-definition"><div><dt>当前节点</dt><dd>{{ workflowTask.nodeKey }}</dd></div><div><dt>任务状态</dt><dd>{{ workflowTask.status === 'CLAIMED' ? '已领取' : '待处理' }}</dd></div><div><dt>候选角色</dt><dd>{{ workflowTask.candidateRole ?? '—' }}</dd></div><div><dt>候选/受理人</dt><dd class="mono-text">{{ workflowTask.assigneeIamUserId ?? workflowTask.candidateIamUserId ?? '角色池待领取' }}</dd></div></dl><p v-else class="workflow-unavailable">当前没有可展示的活动任务，或任务信息不在当前授权范围内。</p></section>
      <section class="panel detail-panel related-tickets-panel"><div class="panel-header"><div><h3>关联工单</h3><p>关系建立与展示均需重新校验两张工单的对象权限。</p></div><button class="button button--secondary button--compact" type="button" @click="openRelation">关联工单</button></div><div v-if="relations.length" class="related-ticket-list"><RouterLink v-for="relation in relations" :key="`${relation.relationType}-${relation.relatedTicket.id}`" :to="`/tickets/${relation.relatedTicket.id}`" class="related-ticket-row"><span class="tag tag--muted">{{ relationLabels[relation.relationType] }}</span><b class="mono-text">{{ relation.relatedTicket.id }}</b><span>{{ relation.relatedTicket.title }}</span><small>{{ typeNames[relation.relatedTicket.type] }} · {{ statusNames[relation.relatedTicket.status] }} · {{ relation.direction === 'OUTBOUND' ? '本单发起' : '对方发起' }}</small></RouterLink></div><p v-else class="workflow-unavailable">暂无当前身份可见的关联工单。</p></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>流程与审批记录</h3><p>流程动作、审批决策和系统事件统一留痕；当前无审批决策时不虚构审批记录。</p></div></div><ol class="ticket-timeline"><li v-for="item in timeline" :key="item.id"><span></span><div><b>{{ item.label }}</b><small>{{ formatFullTime(item.occurredAt) }}<template v-if="item.actor"> · {{ item.actor.displayName }}</template></small><p v-if="item.note">{{ item.note }}</p><em v-if="item.auditEventId">审计：{{ item.auditEventId }}</em></div></li></ol></section>
      <section class="panel detail-panel">
        <div class="panel-header"><div><h3>审批申请记录</h3><p>审批通过不等于已执行；执行结果、执行人和节点迁移均由服务端事务留痕。</p></div></div>
        <div v-if="approvalRequests.length" class="related-ticket-list">
          <div v-for="approval in approvalRequests" :key="approval.id" class="related-ticket-row">
            <span class="tag tag--orange">{{ approval.status === 'PENDING_APPROVAL' ? '待审批' : approval.status === 'EXECUTED' ? '已执行' : approval.status }}</span>
            <b>受控跳转</b><span>{{ approval.sourceNode }} → {{ approval.targetNode }} · {{ approval.reason }}</span>
            <small>申请：{{ formatFullTime(approval.createdAt) }} · {{ approval.applicantIamUserId }}</small>
            <small v-if="approval.approverIamUserId">审批：{{ approval.approverIamUserId }}<template v-if="approval.decidedAt"> · {{ formatFullTime(approval.decidedAt) }}</template></small>
            <small v-if="approval.approvalPolicy">策略快照：{{ approval.approvalPolicy.processKey }} v{{ approval.approvalPolicy.processVersion }} · {{ approval.approvalPolicy.decisionMode === 'ALL_OF' ? '全员会签' : '任一审批' }} · 冻结候选名单仅保存在服务端审计快照</small>
            <small v-for="decision in decisionsForApproval(approval.id)" :key="decision.id">决策：{{ decision.approverIamUserId }} · {{ decision.decision === 'APPROVED' ? '通过' : '驳回' }} · {{ decision.reason }} · {{ formatFullTime(decision.decidedAt) }}</small>
            <small v-if="approval.executedAt">执行：{{ approval.executorIamUserId }} · {{ approval.executedFromNode }} → {{ approval.executedToNode }} · {{ formatFullTime(approval.executedAt) }}</small>
            <small v-else-if="approval.status === 'EXECUTING'">系统正在执行受控迁移；刷新前不会重复提交。</small>
            <small v-else-if="approval.executionFailureReason">执行未完成：{{ approval.executionFailureReason }}</small>
            <template v-for="managementAction in managementActionsForApproval(approval.id)" :key="managementAction.requestId">
              <button class="button button--secondary button--compact" type="button" :disabled="!managementAction.canPreflight || controlledJumpSubmitting" :title="managementAction.disabledReason" @click="openControlledJump(managementAction)">
                {{ managementAction.canExecute ? '预演并执行' : '查看预演' }}
              </button>
              <small v-if="managementAction.disabledReason">当前不可执行：{{ managementAction.disabledReason }}</small>
            </template>
          </div>
        </div>
        <p v-else class="workflow-unavailable">暂无真实审批申请记录。</p>
      </section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>协作人员</h3><p>主办/协办关系由服务端协作规则返回。</p></div></div><ul v-if="participants.length" class="participant-list"><li v-for="participant in participants" :key="`${participant.role}-${participant.identity.iamUserId}`"><span class="participant-avatar">{{ participant.identity.displayName.slice(0, 1) }}</span><div><b>{{ participant.identity.displayName }}</b><small>{{ participant.identity.organizationName }} · {{ participant.identity.positionName ?? '—' }}</small></div><span class="role-pill">{{ participantRole(participant.role) }}</span></li></ul><p v-else class="workflow-unavailable">暂未返回协作人员。</p></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>内部评论</h3><p>仅对具备内部协作权限的当前人员展示。</p></div></div><div v-if="comments.length" class="comment-list"><article v-for="comment in comments" :key="comment.id"><div class="comment-head"><b>{{ comment.author.displayName }}</b><span>{{ formatFullTime(comment.createdAt) }}</span></div><p>{{ comment.content }}</p><small v-if="comment.auditEventId">内部 · 审计：{{ comment.auditEventId }}</small></article></div><p v-else class="workflow-unavailable">暂无可见内部评论，或当前身份无查看权限。</p></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>附件</h3><p>文件须通过服务端隔离与扫描；下载时再次鉴权。</p></div></div><ul v-if="attachments.length" class="knowledge-attachment-list"><li v-for="file in attachments" :key="file.id"><div><b>{{ file.displayFileName }}</b><small>{{ attachmentSize(file.sizeBytes) }} · {{ file.detectedMediaType }}</small><span :class="`scan-state scan-state--${file.scanState.toLowerCase()}`">{{ attachmentScanLabel[file.scanState] }}</span></div><button v-if="file.downloadable && file.scanState === 'SCAN_PASSED'" class="button button--secondary button--compact" type="button" @click="downloadAttachment(file.id)">受鉴权下载</button><span v-else class="attachment-blocked">禁止下载</span></li></ul><p v-else class="workflow-unavailable">暂无可见附件。</p><p class="scan-notice">未通过扫描的附件禁止下载、发布和用于知识库。</p></section>
      <section class="panel detail-panel identity-panel"><div class="panel-header"><div><h3>提交时身份快照</h3><p>保留 IAM ID 与当时组织职位，人员调岗不改写历史。</p></div></div><dl class="detail-definition"><div><dt>姓名</dt><dd>{{ ticket.requester.displayName }}</dd></div><div><dt>IAM 用户 ID</dt><dd class="mono-text">{{ ticket.requester.iamUserId }}</dd></div><div><dt>组织</dt><dd>{{ ticket.requester.organizationName }}</dd></div><div><dt>职位</dt><dd>{{ ticket.requester.positionName ?? '—' }}</dd></div><div><dt>快照时间</dt><dd>{{ formatFullTime(ticket.requester.capturedAt) }}</dd></div></dl></section>
    </div>
    <div v-if="selectedAction" class="modal-backdrop" @mousedown.self="closeAction"><section class="action-modal" role="dialog" aria-modal="true" :aria-label="actionTitle"><div class="modal-heading"><div><span class="eyebrow">工单 {{ ticket.id }} · 版本 v{{ ticket.version }}</span><h3>{{ actionTitle }}</h3><p>{{ actionHelp(selectedAction.code) }}</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeAction">×</button></div><form class="action-form" @submit.prevent="submitAction"><label v-if="needsTarget" class="field"><span>候选处理人 IAM ID <b>*</b></span><input v-model.trim="actionForm.targetIamUserId" maxlength="128" placeholder="例如 iam-u-000063" /><small>只提交候选 ID，不提交角色、组织或最终受让人。</small></label><label class="field"><span>{{ isComment ? '评论目的' : '操作理由' }} <b>*</b></span><select v-if="!isComment" v-model="actionForm.reason"><option value="">请选择标准理由</option><option value="故障影响业务处理">故障影响业务处理</option><option value="按服务目录规则处理">按服务目录规则处理</option><option value="需跨组协同处理">需跨组协同处理</option><option value="用户已确认处理结果">用户已确认处理结果</option><option value="其他已记录原因">其他已记录原因</option></select><input v-else v-model.trim="actionForm.reason" maxlength="500" placeholder="例如：同步排查结论" /></label><label class="field"><span>{{ isComment ? '评论内容' : '处理补充' }}<b v-if="isComment">*</b></span><textarea v-model.trim="actionForm.detail" :maxlength="isComment ? 4000 : 1000" rows="4" :placeholder="isComment ? '仅填写可供内部协作的处理信息，不填写密码、令牌等敏感数据。' : '可选：填写已发布动作表单允许的补充信息。'" /></label><div class="action-form__meta"><span>乐观锁版本</span><b>v{{ ticket.version }}</b><small>提交时由后端校验；版本冲突需刷新后重试。</small></div><p v-if="actionError" class="form-alert form-alert--error">{{ actionError }}</p><p v-if="actionNotice" class="form-alert form-alert--success">{{ actionNotice }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="actionSubmitting" @click="closeAction">取消</button><button class="button button--primary" type="submit" :disabled="actionSubmitting">{{ source === 'demo' ? '演示中禁止提交' : actionSubmitting ? '正在提交…' : '提交至服务端' }}</button></div></form></section></div>
    <div v-if="selectedControlledJump" class="modal-backdrop" @mousedown.self="closeControlledJump">
      <section class="action-modal" role="dialog" aria-modal="true" aria-label="受控跳转预演与执行">
        <div class="modal-heading"><div><span class="eyebrow">工单 {{ ticket.id }} · 版本 v{{ ticket.version }}</span><h3>受控跳转预演</h3><p>预演不改变流程；确认执行时，服务端会再次校验审批、对象权限、版本、节点和候选人。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeControlledJump">×</button></div>
        <div class="action-form">
          <p v-if="controlledJumpSubmitting && !controlledJumpPreflight" class="workflow-unavailable">正在由服务端预演…</p>
          <template v-else-if="controlledJumpPreflight">
            <div class="action-form__meta"><span>当前任务</span><b>{{ controlledJumpPreflight.currentTaskDisposition }}</b><small>执行后由服务端取消旧任务并保留审计。</small></div>
            <div class="action-form__meta"><span>候选人</span><b>{{ controlledJumpPreflight.targetCandidateRole }}</b><small>{{ controlledJumpPreflight.candidateResolution }}</small></div>
            <div class="action-form__meta"><span>SLA / 通知</span><b>{{ controlledJumpPreflight.slaImpact }}</b><small>{{ controlledJumpPreflight.notificationImpact }}</small></div>
            <p v-if="!controlledJumpPreflight.executable" class="form-alert form-alert--error">当前不可执行：{{ controlledJumpPreflight.blockingReasons.join('；') }}</p>
            <p v-else class="form-alert form-alert--success">预演通过。执行会产生流程节点迁移、SLA 重算、通知重算和完整审计记录。</p>
          </template>
          <p v-if="controlledJumpError" class="form-alert form-alert--error">{{ controlledJumpError }}</p>
          <div class="modal-actions"><button class="button button--secondary" type="button" :disabled="controlledJumpSubmitting" @click="closeControlledJump">取消</button><button v-if="controlledJumpPreflight?.executable" class="button button--primary" type="button" :disabled="controlledJumpSubmitting" @click="executeControlledJump">{{ controlledJumpSubmitting ? '服务端执行中…' : '确认执行受控跳转' }}</button></div>
        </div>
      </section>
    </div>
    <div v-if="showRelation" class="modal-backdrop" @mousedown.self="closeRelation"><section class="action-modal" role="dialog" aria-modal="true" aria-label="关联工单"><div class="modal-heading"><div><span class="eyebrow">工单关系</span><h3>关联工单</h3><p>服务端会检查本单可修改权限及目标工单可读权限，不因编号返回而泄露越权工单。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeRelation">×</button></div><form class="action-form" @submit.prevent="submitRelation"><label class="field"><span>关系类型 <b>*</b></span><select v-model="relationForm.relationType"><option value="RELATED">关联工单</option><option value="DUPLICATE_OF">本单重复于目标单</option><option value="PARENT_OF">本单为目标单父工单</option><option value="PROBLEM_REFERENCE">关联问题单</option><option value="CHANGE_REFERENCE">关联变更单</option></select></label><label class="field"><span>目标工单编号 <b>*</b></span><input v-model.trim="relationForm.targetTicketId" maxlength="24" placeholder="例如 TKT-20260822-000001" /><small>仅接受完整编号；不支持按标题搜索或任意跨组织枚举。</small></label><p v-if="relationError" class="form-alert form-alert--error">{{ relationError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="relationSubmitting" @click="closeRelation">取消</button><button class="button button--primary" type="submit" :disabled="relationSubmitting">{{ relationSubmitting ? '关联中…' : '创建受控关联' }}</button></div></form></section></div>
  </template>
</template>
