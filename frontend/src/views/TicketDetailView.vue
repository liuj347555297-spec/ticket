<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type Ticket, type TicketActionCode, type TicketAvailableAction, type TicketComment, type TicketLifecycleAction, type TicketStatus, type TicketTimelineEvent, type TicketType, type TicketWorkAction } from '@/api/tickets'
import { notificationApi, type TicketNotificationSummary } from '@/api/notifications'

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

const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }
const statusNames: Record<TicketStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起' }
const actionLabels: Record<TicketActionCode, string> = {
  SUBMIT: '提交', CLASSIFY: '分类', ASSIGN: '分派', ACCEPT: '受理', START_PROCESSING: '开始处理', REQUEST_USER_FEEDBACK: '待用户反馈', RESOLVE: '解决', CLOSE: '关闭', REOPEN: '重开', CANCEL: '撤销', HOLD: '挂起', RESUME: '恢复', ESCALATE: '升级',
  TRANSFER: '转办', ADD_COLLABORATOR: '添加协办', REMOVE_COLLABORATOR: '移除协办', CLAIM: '抢单', APPOINT_PRIMARY: '指定主办', HANDOVER_SHIFT: '交接班', INTERNAL_COMMENT: '内部评论',
}
const lifecycleCodes: TicketLifecycleAction[] = ['SUBMIT', 'CLASSIFY', 'ASSIGN', 'ACCEPT', 'START_PROCESSING', 'REQUEST_USER_FEEDBACK', 'RESOLVE', 'CLOSE', 'REOPEN', 'CANCEL', 'HOLD', 'RESUME', 'ESCALATE']
const targetRequiredCodes: TicketWorkAction[] = ['TRANSFER', 'ADD_COLLABORATOR', 'REMOVE_COLLABORATOR', 'APPOINT_PRIMARY', 'HANDOVER_SHIFT']
const demoComments: TicketComment[] = [{ id: 'demo-comment-001', visibility: 'INTERNAL', author: { iamUserId: 'iam-u-000063', displayName: '李工', organizationName: '数字化运营中心 / 应用运维组', positionName: '应用运维工程师', capturedAt: '2026-08-19T09:28:00+08:00' }, content: '已关联性能监控检查项，待确认慢查询与缓存命中情况。', createdAt: '2026-08-19T09:30:00+08:00', auditEventId: 'AUD-20260819-004' }]

const availableActions = computed(() => ticket.value?.availableActions ?? [])
const lifecycleActions = computed(() => availableActions.value.filter((item) => lifecycleCodes.includes(item.code as TicketLifecycleAction)))
const workActions = computed(() => availableActions.value.filter((item) => !lifecycleCodes.includes(item.code as TicketLifecycleAction) && item.code !== 'INTERNAL_COMMENT'))
const canComment = computed(() => availableActions.value.some((item) => item.code === 'INTERNAL_COMMENT'))
const actionTitle = computed(() => selectedAction.value ? selectedAction.value.label ?? actionLabels[selectedAction.value.code] : '')
const needsTarget = computed(() => selectedAction.value?.requiresTarget ?? targetRequiredCodes.includes(selectedAction.value?.code as TicketWorkAction))
const isComment = computed(() => selectedAction.value?.code === 'INTERNAL_COMMENT')
const participants = computed(() => ticket.value?.participants?.length ? ticket.value.participants : ticket.value?.assignee ? [{ role: 'PRIMARY' as const, identity: ticket.value.assignee, assignedAt: ticket.value.updatedAt ?? ticket.value.createdAt }] : [])
const timeline = computed<TicketTimelineEvent[]>(() => {
  if (!ticket.value) return []
  if (ticket.value.timeline?.length) return ticket.value.timeline
  const rows = [{ id: 'created', label: '提交工单', occurredAt: ticket.value.createdAt, note: '已记录提交时 IAM 身份快照。', actor: ticket.value.requester }]
  if (ticket.value.assignee) rows.push({ id: 'assigned', label: '进入处理队列', occurredAt: ticket.value.updatedAt ?? ticket.value.createdAt, note: `当前处理人：${ticket.value.assignee.displayName}`, actor: ticket.value.assignee })
  return rows
})

function formatFullTime(value: string): string { return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', hour12: false }).format(new Date(value)) }
function participantRole(role: 'PRIMARY' | 'COLLABORATOR'): string { return role === 'PRIMARY' ? '主办' : '协办' }
function actionHelp(action?: TicketActionCode): string {
  if (action === 'INTERNAL_COMMENT') return '仅内部协作人员可见；内容会由服务端净化、脱敏并审计。'
  if (action === 'CLAIM') return '抢单时不指定处理人，服务端会重新校验候选资格。'
  if (action && targetRequiredCodes.includes(action as TicketWorkAction)) return '目标人员只是候选人；服务端会校验其 IAM 投影、班次、技能和数据范围。'
  return '状态、流程节点、审批、SLA 和最终处理人均由服务端规则决定。'
}
function openAction(action: TicketAvailableAction): void { if (!action.disabledReason) { selectedAction.value = action; actionForm.value = { targetIamUserId: '', reason: '', detail: '' }; actionError.value = ''; actionNotice.value = '' } }
function closeAction(): void { if (!actionSubmitting.value) selectedAction.value = null }
function isLifecycleAction(code: TicketActionCode): code is TicketLifecycleAction { return lifecycleCodes.includes(code as TicketLifecycleAction) }

async function submitAction(): Promise<void> {
  if (!ticket.value || !selectedAction.value) return
  const action = selectedAction.value.code
  actionError.value = ''; actionNotice.value = ''
  if (!actionForm.value.reason.trim() || (needsTarget.value && !actionForm.value.targetIamUserId.trim()) || (isComment.value && !actionForm.value.detail.trim())) { actionError.value = isComment.value ? '请填写评论目的和评论内容。' : '请填写操作理由及必填候选人员。'; return }
  if (source.value === 'demo') { actionNotice.value = '演示模式已阻断提交：此表单不代表权限校验、审批完成或工单状态变更。'; return }
  actionSubmitting.value = true
  try {
    if (action === 'INTERNAL_COMMENT') {
      const comment = await ticketApi.createInternalComment(ticket.value.id, { version: ticket.value.version, reason: actionForm.value.reason.trim(), content: actionForm.value.detail.trim() })
      comments.value = [comment, ...comments.value]; actionNotice.value = '内部评论已由服务端接收并写入审计链。'
    } else if (isLifecycleAction(action)) {
      const result = await ticketApi.executeAction(ticket.value.id, { action, version: ticket.value.version, reason: actionForm.value.reason.trim(), structuredFields: { executionNote: actionForm.value.detail.trim() } })
      ticket.value = result.ticket; actionNotice.value = result.decision.outcome === 'COMPLETED' ? '服务端已完成动作并返回最新工单。' : '服务端已创建后续审批或流程任务，当前不代表动作已完成。'
    } else {
      const result = await ticketApi.executeWorkAction(ticket.value.id, { action: action as TicketWorkAction, version: ticket.value.version, reason: actionForm.value.reason.trim(), targetIamUserId: actionForm.value.targetIamUserId.trim() || undefined, structuredFields: { executionNote: actionForm.value.detail.trim() } })
      ticket.value = { ...result.ticket, participants: result.participants }; actionNotice.value = result.decision.outcome === 'COMPLETED' ? '服务端已返回最新协作关系。' : '服务端已创建后续审批或流程任务，当前不代表协作关系已生效。'
    }
  } catch (error) { actionError.value = error instanceof ApiError ? error.message : '动作提交失败，请刷新工单后重试。' } finally { actionSubmitting.value = false }
}
async function loadComments(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> { if (dataSource === 'demo') { comments.value = demoComments; return }; try { comments.value = (await ticketApi.listInternalComments(ticketId)).items } catch { comments.value = [] } }
async function loadTicket(): Promise<void> {
  const ticketId = String(route.params.ticketId ?? '')
  loading.value = true; errorMessage.value = ''; selectedAction.value = null
  try { const result = await ticketApi.get(ticketId); ticket.value = result.data; source.value = result.source; await loadComments(ticketId, result.source); try { notificationSummary.value = (await notificationApi.ticketSummary(ticketId)).data } catch { notificationSummary.value = null } } catch (error) { ticket.value = null; comments.value = []; notificationSummary.value = null; errorMessage.value = error instanceof ApiError ? error.message : '无法加载此工单，可能已不存在或无权访问。' } finally { loading.value = false }
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
    <div class="ticket-detail-layout">
      <section class="panel detail-panel"><div class="panel-header"><div><h3>问题描述</h3><p>提交人填写的结构化信息与补充说明。</p></div></div><p class="ticket-description">{{ ticket.description || '暂无补充说明。' }}</p><dl class="detail-definition"><div><dt>服务目录</dt><dd>{{ ticket.serviceCatalogItem.name }}</dd></div><div><dt>创建时间</dt><dd>{{ formatFullTime(ticket.createdAt) }}</dd></div><div><dt>当前版本</dt><dd>v{{ ticket.version }}（写操作必须校验）</dd></div></dl></section>
      <aside class="detail-sidebar"><section class="panel detail-panel"><div class="panel-header"><div><h3>处理信息</h3><p>处理关系与状态以服务端为准。</p></div></div><dl class="detail-definition"><div><dt>当前状态</dt><dd>{{ statusNames[ticket.status] }}</dd></div><div><dt>当前处理人</dt><dd>{{ ticket.assignee?.displayName ?? '待后端分派' }}</dd></div><div><dt>处理组织</dt><dd>{{ ticket.assignee?.organizationName ?? '—' }}</dd></div></dl></section></aside>
      <section class="panel detail-panel workflow-panel"><div class="panel-header"><div><h3>流程动作</h3><p>仅显示服务端当前返回的可用动作；页面不自行推断权限。</p></div></div><div v-if="availableActions.length" class="workflow-action-groups"><div><small>生命周期</small><div class="action-row"><button v-for="action in lifecycleActions" :key="action.code" class="button button--secondary button--compact" type="button" :disabled="Boolean(action.disabledReason)" :title="action.disabledReason" @click="openAction(action)">{{ action.label ?? actionLabels[action.code] }}</button><span v-if="!lifecycleActions.length" class="empty-inline">当前无可用生命周期动作</span></div></div><div><small>多人协作</small><div class="action-row"><button v-for="action in workActions" :key="action.code" class="button button--secondary button--compact" type="button" :disabled="Boolean(action.disabledReason)" :title="action.disabledReason" @click="openAction(action)">{{ action.label ?? actionLabels[action.code] }}</button><button v-if="canComment" class="button button--secondary button--compact" type="button" @click="openAction({ code: 'INTERNAL_COMMENT', label: '内部评论' })">内部评论</button><span v-if="!workActions.length && !canComment" class="empty-inline">当前无可用协作动作</span></div></div></div><p v-else class="workflow-unavailable">后端尚未提供工单的可用动作读模型，已隐藏全部写操作以避免前端越权。</p></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>流程时间线</h3><p>正式环境以不可篡改审计事件为准。</p></div></div><ol class="ticket-timeline"><li v-for="item in timeline" :key="item.id"><span></span><div><b>{{ item.label }}</b><small>{{ formatFullTime(item.occurredAt) }}<template v-if="item.actor"> · {{ item.actor.displayName }}</template></small><p v-if="item.note">{{ item.note }}</p><em v-if="item.auditEventId">审计：{{ item.auditEventId }}</em></div></li></ol></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>协作人员</h3><p>主办/协办关系由服务端协作规则返回。</p></div></div><ul v-if="participants.length" class="participant-list"><li v-for="participant in participants" :key="`${participant.role}-${participant.identity.iamUserId}`"><span class="participant-avatar">{{ participant.identity.displayName.slice(0, 1) }}</span><div><b>{{ participant.identity.displayName }}</b><small>{{ participant.identity.organizationName }} · {{ participant.identity.positionName ?? '—' }}</small></div><span class="role-pill">{{ participantRole(participant.role) }}</span></li></ul><p v-else class="workflow-unavailable">暂未返回协作人员。</p></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>内部评论</h3><p>仅对具备内部协作权限的当前人员展示。</p></div></div><div v-if="comments.length" class="comment-list"><article v-for="comment in comments" :key="comment.id"><div class="comment-head"><b>{{ comment.author.displayName }}</b><span>{{ formatFullTime(comment.createdAt) }}</span></div><p>{{ comment.content }}</p><small v-if="comment.auditEventId">内部 · 审计：{{ comment.auditEventId }}</small></article></div><p v-else class="workflow-unavailable">暂无可见内部评论，或当前身份无查看权限。</p></section>
      <section class="panel detail-panel identity-panel"><div class="panel-header"><div><h3>提交时身份快照</h3><p>保留 IAM ID 与当时组织职位，人员调岗不改写历史。</p></div></div><dl class="detail-definition"><div><dt>姓名</dt><dd>{{ ticket.requester.displayName }}</dd></div><div><dt>IAM 用户 ID</dt><dd class="mono-text">{{ ticket.requester.iamUserId }}</dd></div><div><dt>组织</dt><dd>{{ ticket.requester.organizationName }}</dd></div><div><dt>职位</dt><dd>{{ ticket.requester.positionName ?? '—' }}</dd></div><div><dt>快照时间</dt><dd>{{ formatFullTime(ticket.requester.capturedAt) }}</dd></div></dl></section>
    </div>
    <div v-if="selectedAction" class="modal-backdrop" @mousedown.self="closeAction"><section class="action-modal" role="dialog" aria-modal="true" :aria-label="actionTitle"><div class="modal-heading"><div><span class="eyebrow">工单 {{ ticket.id }} · 版本 v{{ ticket.version }}</span><h3>{{ actionTitle }}</h3><p>{{ actionHelp(selectedAction.code) }}</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeAction">×</button></div><form class="action-form" @submit.prevent="submitAction"><label v-if="needsTarget" class="field"><span>候选处理人 IAM ID <b>*</b></span><input v-model.trim="actionForm.targetIamUserId" maxlength="128" placeholder="例如 iam-u-000063" /><small>只提交候选 ID，不提交角色、组织或最终受让人。</small></label><label class="field"><span>{{ isComment ? '评论目的' : '操作理由' }} <b>*</b></span><select v-if="!isComment" v-model="actionForm.reason"><option value="">请选择标准理由</option><option value="故障影响业务处理">故障影响业务处理</option><option value="按服务目录规则处理">按服务目录规则处理</option><option value="需跨组协同处理">需跨组协同处理</option><option value="用户已确认处理结果">用户已确认处理结果</option><option value="其他已记录原因">其他已记录原因</option></select><input v-else v-model.trim="actionForm.reason" maxlength="500" placeholder="例如：同步排查结论" /></label><label class="field"><span>{{ isComment ? '评论内容' : '处理补充' }}<b v-if="isComment">*</b></span><textarea v-model.trim="actionForm.detail" :maxlength="isComment ? 4000 : 1000" rows="4" :placeholder="isComment ? '仅填写可供内部协作的处理信息，不填写密码、令牌等敏感数据。' : '可选：填写已发布动作表单允许的补充信息。'" /></label><div class="action-form__meta"><span>乐观锁版本</span><b>v{{ ticket.version }}</b><small>提交时由后端校验；版本冲突需刷新后重试。</small></div><p v-if="actionError" class="form-alert form-alert--error">{{ actionError }}</p><p v-if="actionNotice" class="form-alert form-alert--success">{{ actionNotice }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="actionSubmitting" @click="closeAction">取消</button><button class="button button--primary" type="submit" :disabled="actionSubmitting">{{ source === 'demo' ? '演示中禁止提交' : actionSubmitting ? '正在提交…' : '提交至服务端' }}</button></div></form></section></div>
  </template>
</template>
