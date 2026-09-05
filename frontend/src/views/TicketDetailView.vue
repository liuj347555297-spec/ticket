<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, RouterLink, useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { ticketApi, type AcceptanceCandidate, type AttachmentScanState, type ControlledJumpPreflight, type NextHandlerCandidate, type Ticket, type TicketActionCode, type TicketAttachment, type TicketAvailableAction, type TicketComment, type TicketLifecycleAction, type TicketRelation, type TicketRelationType, type TicketSla, type TicketSlaResponse, type TicketStatus, type TicketTimelineEvent, type TicketType, type TicketWorkAction } from '@/api/tickets'
import { notificationApi, type TicketNotificationSummary } from '@/api/notifications'
import { useSessionStore } from '@/stores/session'
import TicketProcessingForm from '@/components/TicketProcessingForm.vue'
import TicketKnowledgeSidebar from '@/components/TicketKnowledgeSidebar.vue'
import WorkflowDiagramPanel from '@/components/WorkflowDiagramPanel.vue'
import { processingReasonActions } from '@/utils/processingNotes'
import '@/styles/detail-workflow.css'
import '@/styles/ticket-processing-workspace.css'

const route = useRoute()
const session = useSessionStore()
const processingForm = ref<InstanceType<typeof TicketProcessingForm>>()
const processingDirty = ref(false)
const processingDetailsEditable = ref(false)
const processingDetailsSaving = ref(false)
const activeDetailTab = ref('basic')
const detailTabs = [{ code: 'basic', label: '基础与处理' }, { code: 'relations', label: '关联工单' }, { code: 'workflow', label: '流程图' }, { code: 'approvals', label: '审批记录' }, { code: 'collaboration', label: '协作记录' }, { code: 'history', label: '流转记录' }]
let contextGeneration = 0
let actionOpenSequence = 0
let disposed = false
const actionContext = ref<{ ticketId: string; generation: number; version: number } | null>(null)
function isCurrent(ticketId: string, generation: number): boolean { return !disposed && generation === contextGeneration && String(route.params.ticketId) === ticketId }
const ticket = ref<Ticket | null>(null)
const source = ref<'api' | 'demo'>('api')
const loading = ref(true)
const errorMessage = ref('')
const comments = ref<TicketComment[]>([])
const selectedAction = ref<TicketAvailableAction | null>(null)
const actionSubmitting = ref(false)
const actionNotice = ref('')
const actionError = ref('')
const actionForm = ref({ targetIamUserId: '', targetNode: '', reason: '', detail: '' })
const nextHandlerCandidates = ref<NextHandlerCandidate[]>([])
const notificationSummary = ref<TicketNotificationSummary | null>(null)
const attachments = ref<TicketAttachment[]>([])
const workflowTask = ref<{ nodeKey: string; status: string; candidateRole?: string; candidateIamUserId?: string; assigneeIamUserId?: string } | null>(null)
const assignmentSnapshots = ref<{ nodeKey: string; mode: 'SYSTEM_RANDOM' | 'PREVIOUS_HANDLER_SELECTS' | 'SHARED_QUEUE'; candidateRoles: string[]; policyVersion: number; selectedIamUserId?: string; capturedAt: string }[]>([])
const acceptanceCandidates = ref<AcceptanceCandidate[]>([])
const acceptanceCandidateCount = ref(0)
const workflowParticipants = ref<{ role: 'PRIMARY' | 'COLLABORATOR'; identity: import('@/api/tickets').IdentitySnapshot; assignedAt: string }[]>([])
const approvalRequests = ref<{ id: string; applicantIamUserId: string; sourceNode: string; targetNode: string; reason: string; status: string; createdAt: string; approverIamUserId?: string; decidedAt?: string; executorIamUserId?: string; executionStartedAt?: string; executedAt?: string; executedFromNode?: string; executedToNode?: string; executionFailureReason?: string; approvalPolicy?: { processKey: string; processDefinitionId: string; processVersion: number; candidateRoles: string[]; decisionMode: string; timeoutPolicyVersion: string; escalationPolicyVersion: string; capturedAt: string } }[]>([])
const approvalDecisions = ref<{ id: string; approvalRequestId: string; engineTaskId?: string; approverIamUserId: string; decision: 'APPROVED' | 'REJECTED'; reason: string; decidedAt: string }[]>([])
const handoverRequests = ref<{ id: string; applicantIamUserId: string; targetIamUserId: string; reason: string; status: 'PENDING_CONFIRMATION' | 'ACCEPTED' | 'REJECTED' | 'STALE'; processDefinitionId: string; processDefinitionVersion: number; createdAt: string; decidedAt?: string; decisionReason?: string }[]>([])
const coHandlerRequests = ref<{ id: string; applicantIamUserId: string; targetIamUserId: string; reason: string; status: 'PENDING_CONFIRMATION' | 'ACCEPTED' | 'REJECTED' | 'STALE'; processDefinitionId: string; processDefinitionVersion: number; createdAt: string; decidedAt?: string; decisionReason?: string }[]>([])
const lifecycleApprovalRequests = ref<{ id: string; action: 'HOLD' | 'ESCALATE' | 'CANCEL' | 'REOPEN' | 'ASSIGN' | 'ACCEPT' | 'RESOLVE' | 'CLOSE'; applicantIamUserId: string; reason: string; targetIamUserId?: string; status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'STALE'; processKey: string; processVersion: number; createdAt: string; approverIamUserId?: string; decisionReason?: string; decidedAt?: string; executedAt?: string }[]>([])
const lifecycleApprovalSubmitting = ref<string | null>(null)
const lifecycleApprovalError = ref('')
const controlledJumpActions = ref<{ requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }[]>([])
const selectedControlledJump = ref<{ requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string } | null>(null)
const controlledJumpPreflight = ref<ControlledJumpPreflight | null>(null)
const controlledJumpSubmitting = ref(false)
const controlledJumpError = ref('')
const handoverSubmitting = ref<string | null>(null)
const handoverError = ref('')
const selectedHandoverDecision = ref<{ id: string; decision: 'ACCEPTED' | 'REJECTED' } | null>(null)
const handoverReason = ref('')
const coHandlerSubmitting = ref<string | null>(null)
const coHandlerError = ref('')
const selectedCoHandlerDecision = ref<{ id: string; decision: 'ACCEPTED' | 'REJECTED' } | null>(null)
const coHandlerReason = ref('')
const relations = ref<TicketRelation[]>([])
const showRelation = ref(false)
const relationSubmitting = ref(false)
const relationError = ref('')
const relationForm = ref<{ targetTicketId: string; relationType: TicketRelationType }>({ targetTicketId: '', relationType: 'RELATED' })
const busy = computed(() => processingDetailsSaving.value || actionSubmitting.value || controlledJumpSubmitting.value || Boolean(handoverSubmitting.value) || Boolean(coHandlerSubmitting.value) || Boolean(lifecycleApprovalSubmitting.value) || relationSubmitting.value)

const typeNames: Record<TicketType, string> = { INCIDENT: '故障报修', SERVICE_REQUEST: '服务请求', ACCESS_REQUEST: '账号权限', PROBLEM: '问题管理', CHANGE: '变更申请' }
const statusNames: Record<TicketStatus, string> = { DRAFT: '草稿', SUBMITTED: '已提交', PENDING_CLASSIFICATION: '待分类', PENDING_ASSIGNMENT: '待分派', PENDING_ACCEPTANCE: '待受理', IN_PROGRESS: '处理中', RESOLVED: '已解决', PENDING_USER_FEEDBACK: '待用户反馈', CLOSED: '已关闭', CANCELLED: '已撤销', ON_HOLD: '已挂起' }
const actionLabels: Record<TicketActionCode, string> = {
  CLASSIFY: '分类', ASSIGN: '分派', ACCEPT: '受理', START_PROCESSING: '未解决，退回处理', REQUEST_USER_FEEDBACK: '解决并提交验证', RESOLVE: '已解决，进入关闭', CLOSE: '确认关闭', REOPEN: '重开', CANCEL: '撤销', HOLD: '挂起', RESUME: '恢复', ESCALATE: '升级',
  TRANSFER: '转办', ADD_COHANDLER: '添加协办', CLAIM: '抢单', HANDOVER: '交接班', INTERNAL_COMMENT: '内部评论', CONTROLLED_JUMP_REQUEST: '受控跳转申请',
}
const lifecycleCodes: TicketLifecycleAction[] = ['CLASSIFY', 'ASSIGN', 'ACCEPT', 'START_PROCESSING', 'REQUEST_USER_FEEDBACK', 'RESOLVE', 'CLOSE', 'REOPEN', 'CANCEL', 'HOLD', 'RESUME', 'ESCALATE']
const targetRequiredCodes: TicketWorkAction[] = ['TRANSFER', 'ADD_COHANDLER', 'HANDOVER']
const demoComments: TicketComment[] = [{ id: 'demo-comment-001', visibility: 'INTERNAL', author: { iamUserId: 'iam-u-000063', displayName: '李工', organizationName: '数字化运营中心 / 应用运维组', positionName: '应用运维工程师', capturedAt: '2026-08-19T09:28:00+08:00' }, content: '已关联性能监控检查项，待确认慢查询与缓存命中情况。', createdAt: '2026-08-19T09:30:00+08:00', auditEventId: 'AUD-20260819-004' }]

const availableActions = computed(() => ticket.value?.availableActions ?? [])
const footerActions = computed(() => availableActions.value.filter((item) => item.code !== 'INTERNAL_COMMENT'))
const workActions = computed(() => availableActions.value.filter((item) => !lifecycleCodes.includes(item.code as TicketLifecycleAction) && item.code !== 'INTERNAL_COMMENT'))
const collaborationActions = computed(() => workActions.value.filter((item) => ['TRANSFER', 'ADD_COHANDLER', 'HANDOVER', 'CLAIM'].includes(item.code)))
const canComment = computed(() => availableActions.value.some((item) => item.code === 'INTERNAL_COMMENT' && !item.disabledReason))
const actionTitle = computed(() => selectedAction.value ? selectedAction.value.label ?? actionLabels[selectedAction.value.code] : '')
const needsTarget = computed(() => selectedAction.value?.requiresTarget ?? targetRequiredCodes.includes(selectedAction.value?.code as TicketWorkAction))
const isComment = computed(() => selectedAction.value?.code === 'INTERNAL_COMMENT')
const requiresReason = computed(() => Boolean(selectedAction.value && processingReasonActions.includes(selectedAction.value.code)))
const participants = computed(() => workflowParticipants.value.length ? workflowParticipants.value : ticket.value?.participants?.length ? ticket.value.participants : ticket.value?.assignee ? [{ role: 'PRIMARY' as const, identity: ticket.value.assignee, assignedAt: ticket.value.updatedAt ?? ticket.value.createdAt }] : [])
const currentAssignee = computed(() => participants.value.find((participant) => participant.role === 'PRIMARY')?.identity ?? ticket.value?.assignee)
const currentAssigneeLabel = computed(() => currentAssignee.value
  ? currentAssignee.value.iamUserId === session.currentUser?.iamUserId ? `我（${currentAssignee.value.displayName}）` : currentAssignee.value.displayName
  : '待后端分派')
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
  if (action === 'INTERNAL_COMMENT') return '处理意见以纯文本保存，仅内部协作人员可见；请勿填写密码、令牌等敏感数据。'
  if (action === 'CLAIM') return '抢单时不指定处理人，服务端会重新校验候选资格。'
  if (action === 'ACCEPT' || action === 'RESOLVE' || action === 'CLOSE') return '日常动作会直接推进；若管理员已发布匹配的审批策略，服务端会返回待审批且不提前改变状态。'
  if (action === 'ASSIGN') return '提交后创建独立 Flowable 审批；审批通过且工单版本未变化时才会分派。'
  if (action && targetRequiredCodes.includes(action as TicketWorkAction)) return '目标人员只是候选人；服务端会校验其 IAM 投影、班次、技能和数据范围。'
  return '状态、流程节点、审批、SLA 和最终处理人均由服务端规则决定。'
}
function workflowActionLabel(action: string): string {
  const labels: Record<string, string> = {
    WORKFLOW_STARTED: '流程已启动', WORKFLOW_CLASSIFY: '完成分类', WORKFLOW_ASSIGN: '完成分派', WORKFLOW_ACCEPT: '已受理',
    WORKFLOW_REQUEST_USER_FEEDBACK: '请求用户反馈', WORKFLOW_RESOLVE: '已解决', WORKFLOW_CLOSE: '已关闭', WORKFLOW_REOPEN: '已重开',
    WORKFLOW_CANCEL: '已撤销', WORKFLOW_HOLD: '已挂起', WORKFLOW_RESUME: '已恢复', WORKFLOW_ESCALATE: '已升级',
    WORKFLOW_CLAIM: '已抢单', WORKFLOW_TRANSFER: '已转办', WORKFLOW_ADD_COHANDLER: '已发起协办确认', WORKFLOW_HANDOVER: '已发起交接班确认', HANDOVER_ACCEPTED: '交接班已确认', HANDOVER_REJECTED: '交接班已拒绝', HANDOVER_STALE: '交接班已失效', COHANDLER_ACCEPTED: '协办已确认', COHANDLER_REJECTED: '协办已拒绝', COHANDLER_STALE: '协办确认已失效', WORKFLOW_INTERNAL_COMMENT: '新增内部评论', CONTROLLED_JUMP_APPROVED: '受控跳转审批通过', CONTROLLED_JUMP_REJECTED: '受控跳转审批拒绝', CONTROLLED_JUMP_EXECUTED: '受控跳转已执行',
  }
  return labels[action] ?? action
}
function openHandoverDecision(request: { id: string }, decision: 'ACCEPTED' | 'REJECTED'): void {
  handoverError.value = ''; handoverReason.value = ''; selectedHandoverDecision.value = { id: request.id, decision }
}
async function decideHandover(): Promise<void> {
  if (!ticket.value || source.value === 'demo' || busy.value) return
  const decision = selectedHandoverDecision.value
  const id = ticket.value.id, generation = contextGeneration
  if (!decision || handoverReason.value.trim().length < 5) { handoverError.value = '交接确认需填写至少 5 个字符的说明。'; return }
  handoverError.value = ''; handoverSubmitting.value = decision.id
  try {
    await ticketApi.decideHandover(id, decision.id, decision.decision, handoverReason.value.trim())
    if (!isCurrent(id, generation)) return
    await loadWorkflow(id, 'api')
    if (isCurrent(id, generation)) selectedHandoverDecision.value = null
  } catch { if (isCurrent(id, generation)) handoverError.value = '交接确认未完成，请核对记录后重试。' }
  finally { if (isCurrent(id, generation)) handoverSubmitting.value = null }
}
function openCoHandlerDecision(request: { id: string }, decision: 'ACCEPTED' | 'REJECTED'): void {
  coHandlerError.value = ''; coHandlerReason.value = ''; selectedCoHandlerDecision.value = { id: request.id, decision }
}
async function decideCoHandler(): Promise<void> {
  if (!ticket.value || source.value === 'demo' || busy.value) return
  const decision = selectedCoHandlerDecision.value
  const id = ticket.value.id, generation = contextGeneration
  if (!decision || coHandlerReason.value.trim().length < 5) { coHandlerError.value = '协办确认需填写至少 5 个字符的说明。'; return }
  coHandlerError.value = ''; coHandlerSubmitting.value = decision.id
  try {
    await ticketApi.decideCoHandler(id, decision.id, decision.decision, coHandlerReason.value.trim())
    if (!isCurrent(id, generation)) return
    await loadWorkflow(id, 'api')
    if (isCurrent(id, generation)) selectedCoHandlerDecision.value = null
  } catch { if (isCurrent(id, generation)) coHandlerError.value = '协办确认未完成，请核对记录后重试。' }
  finally { if (isCurrent(id, generation)) coHandlerSubmitting.value = null }
}
async function decideLifecycleApproval(requestId: string, decision: 'APPROVED' | 'REJECTED'): Promise<void> {
  if (!ticket.value || source.value === 'demo' || busy.value) return
  const id = ticket.value.id, generation = contextGeneration
  lifecycleApprovalSubmitting.value = requestId; lifecycleApprovalError.value = ''
  try {
    await ticketApi.decideLifecycleActionApproval(id, requestId, decision, decision === 'APPROVED' ? '已核验风险与处理依据，同意执行。' : '当前风险依据不足，退回补充后再申请。')
    if (!isCurrent(id, generation)) return
    const result = await ticketApi.get(id)
    if (!isCurrent(id, generation)) return
    ticket.value = result.data
    await Promise.all([loadWorkflow(id, 'api'), loadSla(id, 'api')])
  } catch { if (isCurrent(id, generation)) lifecycleApprovalError.value = '审批未确认完成，请核对最新记录。' }
  finally { if (isCurrent(id, generation)) lifecycleApprovalSubmitting.value = null }
}
async function openAction(action: TicketAvailableAction): Promise<void> {
  if (!ticket.value || busy.value || source.value === 'demo') return
  const allowed = availableActions.value.find((item) => item.code === action.code)
  if (!allowed || allowed.disabledReason) return
  if (processingDirty.value && processingDetailsEditable.value && allowed.code !== 'CLAIM') {
    const saved = await processingForm.value?.saveDetails()
    if (!saved) { activeDetailTab.value = 'basic'; return }
  }
  const draft = processingForm.value?.prepareAction(allowed.code)
  if (!draft) { activeDetailTab.value = 'basic'; return }
  const sequence = ++actionOpenSequence
  const current = { ticketId: ticket.value.id, generation: contextGeneration, version: ticket.value.version }
  actionContext.value = current
  selectedAction.value = allowed
  actionForm.value = { targetIamUserId: '', targetNode: '', reason: draft.reason, detail: draft.detail }
  actionError.value = ''; actionNotice.value = ''; nextHandlerCandidates.value = []
  void nextTick(() => document.querySelector<HTMLElement>('.action-modal input, .action-modal textarea, .action-modal select')?.focus())
  if (allowed.requiresTarget || targetRequiredCodes.includes(allowed.code as TicketWorkAction)) {
    try {
      const candidates = allowed.code === 'ACCEPT' ? await ticketApi.getNextHandlerCandidates(current.ticketId)
        : allowed.code === 'ASSIGN' ? await ticketApi.getAssignmentCandidates(current.ticketId)
          : await ticketApi.getTransferCandidates(current.ticketId)
      if (isCurrent(current.ticketId, current.generation) && sequence === actionOpenSequence) nextHandlerCandidates.value = candidates
    } catch { if (isCurrent(current.ticketId, current.generation) && sequence === actionOpenSequence) actionError.value = '无法读取下一处理人候选列表，请稍后重试。' }
  }
}
async function saveProcessingDetails(): Promise<void> {
  if (!processingForm.value || processingDetailsSaving.value || !processingDetailsEditable.value) return
  await processingForm.value.saveDetails()
}
function onProcessingState(state: { editable: boolean; saving: boolean }): void {
  processingDetailsEditable.value = state.editable
  processingDetailsSaving.value = state.saving
}
function referenceKnowledge(reference: { id: string; title: string; url: string }): void {
  activeDetailTab.value = 'basic'
  processingForm.value?.insertReference(reference)
}
function closeAction(): void { if (!actionSubmitting.value) { actionOpenSequence++; selectedAction.value = null } }
function onActionKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') { event.preventDefault(); closeAction(); return }
  if (event.key !== 'Tab') return
  const nodes = Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled])'))
  if (!nodes.length) return
  if (event.shiftKey && document.activeElement === nodes[0]) { event.preventDefault(); nodes[nodes.length - 1].focus() }
  else if (!event.shiftKey && document.activeElement === nodes[nodes.length - 1]) { event.preventDefault(); nodes[0].focus() }
}
function managementActionsForApproval(requestId: string): { requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }[] { return controlledJumpActions.value.filter((item) => item.requestId === requestId) }
function decisionsForApproval(requestId: string): { id: string; approverIamUserId: string; decision: 'APPROVED' | 'REJECTED'; reason: string; decidedAt: string }[] { return approvalDecisions.value.filter((item) => item.approvalRequestId === requestId) }
async function openControlledJump(action: { requestId: string; canPreflight: boolean; canExecute: boolean; disabledReason?: string }): Promise<void> {
  if (!ticket.value || source.value === 'demo' || !action.canPreflight || busy.value) return
  const id = ticket.value.id, generation = contextGeneration
  selectedControlledJump.value = action; controlledJumpPreflight.value = null; controlledJumpError.value = ''; controlledJumpSubmitting.value = true
  try { const result = await ticketApi.preflightControlledJump(id, action.requestId); if (isCurrent(id, generation)) controlledJumpPreflight.value = result }
  catch { if (isCurrent(id, generation)) controlledJumpError.value = '预演失败，请刷新后重试。' }
  finally { if (isCurrent(id, generation)) controlledJumpSubmitting.value = false }
}
function closeControlledJump(): void { if (!controlledJumpSubmitting.value) selectedControlledJump.value = null }
async function executeControlledJump(): Promise<void> {
  if (!ticket.value || !selectedControlledJump.value || !controlledJumpPreflight.value?.executable || busy.value) return
  const id = ticket.value.id, generation = contextGeneration, version = ticket.value.version, requestId = selectedControlledJump.value.requestId
  controlledJumpError.value = ''; controlledJumpSubmitting.value = true
  try {
    const result = await ticketApi.executeControlledJump(id, requestId, version)
    if (!isCurrent(id, generation)) return
    ticket.value = result
    await Promise.all([loadWorkflow(id, 'api'), loadComments(id, 'api'), loadSla(id, 'api')])
    if (isCurrent(id, generation)) selectedControlledJump.value = null
  } catch { if (isCurrent(id, generation)) controlledJumpError.value = '执行未确认完成，请刷新工单后核对。' }
  finally { if (isCurrent(id, generation)) controlledJumpSubmitting.value = false }
}
function openRelation(): void { relationForm.value = { targetTicketId: '', relationType: 'RELATED' }; relationError.value = ''; showRelation.value = true }
function closeRelation(): void { if (!relationSubmitting.value) showRelation.value = false }
async function submitRelation(): Promise<void> {
  if (!ticket.value || busy.value || source.value === 'demo') return
  if (!/^TKT-\d{8}-\d{6}$/.test(relationForm.value.targetTicketId)) { relationError.value = '请填写有效的工单编号。'; return }
  const id = ticket.value.id, generation = contextGeneration
  relationError.value = ''; relationSubmitting.value = true
  try {
    await ticketApi.createRelation(id, { targetTicketId: relationForm.value.targetTicketId, relationType: relationForm.value.relationType })
    if (!isCurrent(id, generation)) return
    const result = await ticketApi.listRelations(id)
    if (isCurrent(id, generation)) { relations.value = result; showRelation.value = false }
  } catch { if (isCurrent(id, generation)) relationError.value = '关联未确认完成，请核对关系后重试。' }
  finally { if (isCurrent(id, generation)) relationSubmitting.value = false }
}
async function submitAction(): Promise<void> {
  const current = actionContext.value
  if (!ticket.value || !selectedAction.value || actionSubmitting.value || !current || !isCurrent(current.ticketId, current.generation)) return
  const action = selectedAction.value.code
  const allowed = availableActions.value.find((item) => item.code === action)
  actionError.value = ''; actionNotice.value = ''
  if (!allowed || allowed.disabledReason || ticket.value.version !== current.version) { actionError.value = '当前操作或工单版本已变化，请关闭确认并重新加载。'; return }
  const reason = [actionForm.value.reason.trim(), actionForm.value.detail.trim()].filter(Boolean).join('\n补充说明：')
  if ((requiresReason.value && !actionForm.value.reason.trim()) || (needsTarget.value && !actionForm.value.targetIamUserId.trim()) || (isComment.value && !actionForm.value.detail.trim())) { actionError.value = needsTarget.value ? '请选择下一处理人并填写操作理由。' : '请填写操作理由和处理内容。'; return }
  if (action === 'CONTROLLED_JUMP_REQUEST' && !processNodes.some((node) => node.code === actionForm.value.targetNode)) { actionError.value = '请选择允许的目标流程节点。'; return }
  if ((isComment.value && actionForm.value.detail.trim().length > 2000) || (!isComment.value && reason.length > 1000)) { actionError.value = isComment.value ? '内部处理意见最多 2000 字符。' : '操作理由与补充说明合计最多 1000 字符。'; return }
  if (source.value === 'demo') { actionError.value = '演示模式不允许执行工单操作。'; return }
  actionSubmitting.value = true
  try {
    const result = await ticketApi.executeAction(current.ticketId, {
      action, version: current.version,
      reason: isComment.value ? actionForm.value.reason.trim() : reason,
      ...(isComment.value ? { comment: actionForm.value.detail.trim() } : {}),
      ...(actionForm.value.targetIamUserId.trim() ? { targetIamUserId: actionForm.value.targetIamUserId.trim() } : {}),
      ...(action === 'CONTROLLED_JUMP_REQUEST' ? { targetNode: actionForm.value.targetNode as 'classify' | 'assign' | 'accept' | 'processing' | 'user_feedback' | 'closure' } : {}),
    })
    if (!isCurrent(current.ticketId, current.generation)) return
    ticket.value = result.ticket
    await Promise.all([loadWorkflow(current.ticketId, source.value), loadComments(current.ticketId, source.value), loadSla(current.ticketId, source.value)])
    if (!isCurrent(current.ticketId, current.generation)) return
    await processingForm.value?.load()
    processingForm.value?.acknowledgeAction(action)
    selectedAction.value = null
    actionContext.value = null
    actionNotice.value = action === 'INTERNAL_COMMENT' ? '处理意见已保存，可在右侧内部沟通查看。' : result.decision.outcome === 'COMPLETED' ? '服务端已完成动作并返回最新工单。' : '已提交审批或后续流程任务，尚不代表动作执行完成。'
  } catch (error) {
    if (isCurrent(current.ticketId, current.generation)) actionError.value = error instanceof ApiError && error.status === 409 ? '工单已被其他操作更新，请保留处理内容并重新核对版本。' : '本次操作未确认完成，填写内容已保留。请核对最新记录后再操作。'
  } finally { if (isCurrent(current.ticketId, current.generation)) actionSubmitting.value = false }
}
async function loadComments(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  const generation = contextGeneration
  if (dataSource === 'demo') { if (isCurrent(ticketId, generation)) comments.value = demoComments; return }
  try { const result = await ticketApi.listInternalComments(ticketId); if (isCurrent(ticketId, generation)) comments.value = result.items }
  catch { if (isCurrent(ticketId, generation)) comments.value = [] }
}
async function loadWorkflow(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  const generation = contextGeneration
  if (!ticket.value || dataSource === 'demo') return
  try {
    const overview = await ticketApi.getWorkflowOverview(ticketId)
    if (!isCurrent(ticketId, generation) || !ticket.value) return
    workflowTask.value = overview.tasks.find((task) => task.status === 'OPEN' || task.status === 'CLAIMED') ?? null
    assignmentSnapshots.value = overview.assignmentSnapshots ?? []
    acceptanceCandidates.value = overview.acceptanceCandidates ?? []
    acceptanceCandidateCount.value = overview.candidateCount ?? acceptanceCandidates.value.length
    workflowParticipants.value = overview.participants.map((participant) => ({ ...participant, role: participant.role === 'CO_HANDLER' ? 'COLLABORATOR' : 'PRIMARY' }))
    approvalRequests.value = overview.approvalRequests
    approvalDecisions.value = overview.approvalDecisions ?? []
    handoverRequests.value = overview.handoverRequests ?? []
    coHandlerRequests.value = overview.coHandlerRequests ?? []
    lifecycleApprovalRequests.value = overview.lifecycleApprovalRequests ?? []
    controlledJumpActions.value = overview.controlledJumpActions ?? []
    ticket.value = {
      ...ticket.value, availableActions: overview.availableActions,
      timeline: overview.events.map((event) => ({
        id: `workflow-${event.id}`, label: workflowActionLabel(event.action), occurredAt: event.occurredAt,
        note: event.requestId === 'system' ? undefined : `请求追踪：${event.requestId}`,
        actor: { iamUserId: event.actorIamUserId, displayName: event.actorIamUserId, organizationName: '受控 IAM 投影', capturedAt: event.occurredAt },
      })),
    }
  } catch {
    if (!isCurrent(ticketId, generation) || !ticket.value) return
    ticket.value = { ...ticket.value, availableActions: [] }; workflowTask.value = null; assignmentSnapshots.value = []; acceptanceCandidates.value = []; acceptanceCandidateCount.value = 0; workflowParticipants.value = []; approvalRequests.value = []; approvalDecisions.value = []; handoverRequests.value = []; coHandlerRequests.value = []; lifecycleApprovalRequests.value = []; controlledJumpActions.value = []
  }
}
async function loadAttachments(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  const generation = contextGeneration
  if (dataSource === 'demo') { if (isCurrent(ticketId, generation)) attachments.value = ticket.value?.attachments ?? []; return }
  try { const result = await ticketApi.listAttachments(ticketId); if (isCurrent(ticketId, generation)) attachments.value = result.items }
  catch { if (isCurrent(ticketId, generation)) attachments.value = [] }
}
async function loadSla(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  const generation = contextGeneration
  if (!ticket.value || dataSource === 'demo') return
  try { const result = await ticketApi.getSla(ticketId); if (isCurrent(ticketId, generation) && ticket.value) ticket.value = { ...ticket.value, sla: mapTicketSla(result) } }
  catch { /* Omitted SLA is not a zero target. */ }
}
async function loadRelations(ticketId: string, dataSource: 'api' | 'demo'): Promise<void> {
  const generation = contextGeneration
  if (dataSource === 'demo') { if (isCurrent(ticketId, generation)) relations.value = []; return }
  try { const result = await ticketApi.listRelations(ticketId); if (isCurrent(ticketId, generation)) relations.value = result }
  catch { if (isCurrent(ticketId, generation)) relations.value = [] }
}
async function loadTicket(): Promise<void> {
  const ticketId = String(route.params.ticketId ?? '')
  const generation = ++contextGeneration
  actionOpenSequence++
  processingForm.value?.resetDrafts()
  processingDirty.value = false; ticket.value = null
  processingDetailsEditable.value = false; processingDetailsSaving.value = false
  loading.value = true; errorMessage.value = ''; selectedAction.value = null; actionContext.value = null
  actionForm.value = { targetIamUserId: '', targetNode: '', reason: '', detail: '' }; nextHandlerCandidates.value = []
  handoverReason.value = ''; coHandlerReason.value = ''; relationForm.value = { targetTicketId: '', relationType: 'RELATED' }
  controlledJumpPreflight.value = null; controlledJumpError.value = ''; handoverError.value = ''; coHandlerError.value = ''; relationError.value = ''; lifecycleApprovalError.value = ''
  selectedControlledJump.value = null; selectedHandoverDecision.value = null; selectedCoHandlerDecision.value = null; showRelation.value = false
  workflowTask.value = null; assignmentSnapshots.value = []; acceptanceCandidates.value = []; acceptanceCandidateCount.value = 0; workflowParticipants.value = []; approvalRequests.value = []; approvalDecisions.value = []; handoverRequests.value = []; coHandlerRequests.value = []; lifecycleApprovalRequests.value = []; controlledJumpActions.value = []
  comments.value = []; attachments.value = []; relations.value = []; notificationSummary.value = null
  actionNotice.value = ''; actionError.value = ''; activeDetailTab.value = 'basic'
  actionSubmitting.value = false; controlledJumpSubmitting.value = false; handoverSubmitting.value = null; coHandlerSubmitting.value = null; lifecycleApprovalSubmitting.value = null; relationSubmitting.value = false
  if (!session.currentUser || session.loading) { loading.value = session.loading; errorMessage.value = session.loading ? '' : '请先确认当前登录身份后再打开工单。'; return }
  try {
    const result = await ticketApi.get(ticketId)
    if (!isCurrent(ticketId, generation)) return
    ticket.value = result.data; source.value = result.source
    await Promise.all([loadWorkflow(ticketId, result.source), loadComments(ticketId, result.source), loadAttachments(ticketId, result.source), loadSla(ticketId, result.source), loadRelations(ticketId, result.source)])
    if (!isCurrent(ticketId, generation)) return
    try { const summary = await notificationApi.ticketSummary(ticketId); if (isCurrent(ticketId, generation)) notificationSummary.value = summary.data }
    catch { if (isCurrent(ticketId, generation)) notificationSummary.value = null }
  } catch {
    if (isCurrent(ticketId, generation)) { ticket.value = null; errorMessage.value = '无法加载此工单，可能已不存在或无权访问。' }
  } finally { if (isCurrent(ticketId, generation)) loading.value = false }
}
watch(() => JSON.stringify([route.params.ticketId, session.loading, session.currentUser?.iamUserId, session.currentUser?.organizationIamOrganizationId, session.authorization]), loadTicket, { immediate: true, flush: 'sync' })
function confirmLeave(): boolean {
  if (busy.value) return false
  if (!processingDirty.value && !selectedAction.value) return true
  return window.confirm('处理内容尚未提交，离开后不会保留。确定离开当前工单吗？')
}
onBeforeRouteLeave(confirmLeave)
onBeforeRouteUpdate(confirmLeave)
function beforeUnload(event: BeforeUnloadEvent): void { if (processingDirty.value || busy.value || selectedAction.value) { event.preventDefault(); event.returnValue = '' } }
window.addEventListener('beforeunload', beforeUnload)
onBeforeUnmount(() => { disposed = true; contextGeneration++; selectedAction.value = null; window.removeEventListener('beforeunload', beforeUnload) })
</script>

<template>
  <section class="ticket-processing-workspace">
  <div class="detail-nav"><RouterLink to="/tickets">← 返回我的工单</RouterLink><span> / 处理工单</span></div>
  <div v-if="loading" class="panel compact-loading">正在加载工单详情…</div>
  <div v-else-if="errorMessage" class="panel empty-state"><span class="empty-icon">!</span><h3>无法打开工单</h3><p>{{ errorMessage }}</p></div>
  <template v-else-if="ticket">
    <div class="page-heading detail-heading"><div><div class="eyebrow">{{ ticket.id }} · {{ typeNames[ticket.type] }}</div><h2>{{ ticket.title }}</h2><div class="tag-row"><span class="tag" :class="ticket.priority === 'P1' ? 'tag--red' : 'tag--blue'">{{ ticket.priority }}</span><span class="status-pill" :class="`status-pill--${ticket.status.toLowerCase()}`">{{ statusNames[ticket.status] }}</span><span v-for="tag in ticket.tags ?? []" :key="tag.name" class="tag tag--muted">{{ tag.name }}</span></div></div></div>
    <p v-if="source === 'demo'" class="demo-notice">演示数据：动作表单仅供预览，已阻断提交；不代表当前身份、权限、审批或状态变更。</p>
    <section v-if="notificationSummary" class="ticket-notification-summary" :class="{ 'ticket-notification-summary--unread': notificationSummary.unreadCount > 0 }"><span aria-hidden="true">♢</span><div><b>{{ notificationSummary.unreadCount ? `本工单有 ${notificationSummary.unreadCount} 条未读通知` : '本工单暂无未读通知' }}</b><small v-if="notificationSummary.latest">最近：{{ notificationSummary.latest.title }} · {{ formatFullTime(notificationSummary.latest.createdAt) }}</small></div><RouterLink to="/notifications">查看消息中心</RouterLink></section>
    <p v-if="actionNotice" class="form-alert form-alert--success" role="status">{{ actionNotice }}</p>
    <div class="processing-layout">
      <section class="panel processing-main" :inert="Boolean(selectedAction)">
        <header class="processing-document-heading"><span>工单编号：{{ ticket.id }}</span><h3>{{ typeNames[ticket.type] }} · {{ ticket.serviceCatalogItem.name }}</h3><b>{{ statusNames[ticket.status] }}</b></header>
        <nav class="processing-tabs" aria-label="工单处理页签"><button v-for="tab in detailTabs" :key="tab.code" type="button" :class="{ 'is-active': activeDetailTab === tab.code }" :aria-pressed="activeDetailTab === tab.code" :disabled="busy" @click="activeDetailTab = tab.code">{{ tab.label }}</button></nav>
        <div v-show="activeDetailTab === 'basic'" class="processing-main-content">
      <section class="panel detail-panel identity-panel"><div class="panel-header"><div><h3>申请信息</h3><p>以下为工单提交时的申请人和所属单位信息。</p></div></div><dl class="detail-definition"><div><dt>申请人</dt><dd>{{ ticket.requester.displayName }}</dd></div><div><dt>申请人部门</dt><dd>{{ ticket.requester.organizationName }}</dd></div><div><dt>职位</dt><dd>{{ ticket.requester.positionName ?? '—' }}</dd></div><div><dt>申请时间</dt><dd>{{ formatFullTime(ticket.createdAt) }}</dd></div></dl></section>
      <section class="panel detail-panel"><div class="panel-header"><div><h3>问题描述</h3><p>提交人填写的结构化信息与补充说明。</p></div></div><div v-if="ticket.descriptionFormat === 'RICH_TEXT' && ticket.descriptionHtml" class="ticket-description ticket-description--rich" v-html="ticket.descriptionHtml"></div><p v-else class="ticket-description">{{ ticket.description || '暂无补充说明。' }}</p><dl class="detail-definition"><div><dt>服务目录</dt><dd>{{ ticket.serviceCatalogItem.name }}</dd></div><div><dt>创建时间</dt><dd>{{ formatFullTime(ticket.createdAt) }}</dd></div><div><dt>当前版本</dt><dd>v{{ ticket.version }}（写操作必须校验）</dd></div></dl></section>
          <TicketProcessingForm ref="processingForm" :key="ticket.id" :ticket="ticket" :actions="availableActions" :disabled="actionSubmitting || source === 'demo'" :assignee-name="currentAssignee ? currentAssigneeLabel : undefined" @dirty-change="processingDirty = $event" @state-change="onProcessingState" />
      <section class="panel detail-panel"><div class="panel-header"><div><h3>附件</h3><p>文件须通过服务端隔离与扫描；下载时再次鉴权。</p></div></div><ul v-if="attachments.length" class="knowledge-attachment-list"><li v-for="file in attachments" :key="file.id"><div><b>{{ file.displayFileName }}</b><small>{{ attachmentSize(file.sizeBytes) }} · {{ file.detectedMediaType }}</small><span :class="`scan-state scan-state--${file.scanState.toLowerCase()}`">{{ attachmentScanLabel[file.scanState] }}</span></div><button v-if="file.downloadable && file.scanState === 'SCAN_PASSED'" class="button button--secondary button--compact" type="button" @click="downloadAttachment(file.id)">受鉴权下载</button><span v-else class="attachment-blocked">禁止下载</span></li></ul><p v-else class="workflow-unavailable">暂无可见附件。</p><p class="scan-notice">未通过扫描的附件禁止下载、发布和用于知识库。</p></section>
        </div>
        <div v-show="activeDetailTab === 'relations'" class="processing-main-content">      <section class="panel detail-panel related-tickets-panel"><div class="panel-header"><div><h3>关联工单</h3><p>关系建立与展示均需重新校验两张工单的对象权限。</p></div><button class="button button--secondary button--compact" type="button" @click="openRelation">关联工单</button></div><div v-if="relations.length" class="related-ticket-list"><RouterLink v-for="relation in relations" :key="`${relation.relationType}-${relation.relatedTicket.id}`" :to="`/tickets/${relation.relatedTicket.id}`" class="related-ticket-row"><span class="tag tag--muted">{{ relationLabels[relation.relationType] }}</span><b class="mono-text">{{ relation.relatedTicket.id }}</b><span>{{ relation.relatedTicket.title }}</span><small>{{ typeNames[relation.relatedTicket.type] }} · {{ statusNames[relation.relatedTicket.status] }} · {{ relation.direction === 'OUTBOUND' ? '本单发起' : '对方发起' }}</small></RouterLink></div><p v-else class="workflow-unavailable">暂无当前身份可见的关联工单。</p></section>
</div>
        <div v-show="activeDetailTab === 'workflow'" class="processing-main-content">      <section class="panel detail-panel process-overview-panel process-summary-panel"><div class="panel-header"><div><h3>流程摘要</h3><p>节点和候选资格均来自服务端工作流读模型。</p></div></div><WorkflowDiagramPanel :ticket-id="ticket.id" :active="activeDetailTab === 'workflow'" /><dl v-if="workflowTask" class="detail-definition process-task-definition"><div><dt>当前节点</dt><dd>{{ workflowTask.nodeKey }}</dd></div><div><dt>任务状态</dt><dd>{{ workflowTask.status === 'CLAIMED' ? '已领取' : '待处理' }}</dd></div><div><dt>候选角色</dt><dd>{{ workflowTask.candidateRole ?? '—' }}</dd></div><div><dt>候选/受理人</dt><dd>{{ currentAssignee ? currentAssigneeLabel : acceptanceCandidateCount ? `共享队列待抢单（${acceptanceCandidateCount} 人）` : '角色池待领取' }}</dd></div></dl><div v-if="acceptanceCandidates.length" class="acceptance-candidates"><div class="acceptance-candidates__heading"><b>可抢单人员（{{ acceptanceCandidateCount }}）</b></div><ul class="acceptance-candidate-list"><li v-for="candidate in acceptanceCandidates" :key="candidate.iamUserId"><span class="participant-avatar">{{ candidate.displayName.slice(0, 1) }}</span><span><b>{{ candidate.displayName }}</b><small>{{ [candidate.organizationName, candidate.positionName].filter(Boolean).join(' · ') || '在岗人员' }}</small></span></li></ul></div><div v-if="assignmentSnapshots.length" class="routing-snapshot-list"><small v-for="item in assignmentSnapshots" :key="`${item.nodeKey}-${item.capturedAt}`">{{ item.nodeKey }} · {{ item.mode === 'SYSTEM_RANDOM' ? '系统分派' : item.mode === 'SHARED_QUEUE' ? '共享队列' : '上一节点指定' }} · 策略 v{{ item.policyVersion }} · {{ item.selectedIamUserId ?? '未分派' }}</small></div><p v-else class="workflow-unavailable">当前没有可展示的活动任务，或任务信息不在当前授权范围内。</p></section>
</div>
        <div v-show="activeDetailTab === 'approvals'" class="processing-main-content">      <section class="panel detail-panel">
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
      <section v-if="lifecycleApprovalRequests.length" class="panel detail-panel">
        <div class="panel-header"><div><h3>生命周期动作审批</h3><p>分派、受理、解决、关闭及高风险动作均先进入独立 Flowable 审批；审批完成后由服务端按冻结版本自动执行。</p></div></div>
        <p v-if="lifecycleApprovalError" class="form-alert form-alert--error">{{ lifecycleApprovalError }}</p>
        <div class="related-ticket-list"><div v-for="approval in lifecycleApprovalRequests" :key="approval.id" class="related-ticket-row">
          <span class="tag" :class="approval.status === 'EXECUTED' ? 'tag--green' : approval.status === 'PENDING_APPROVAL' ? 'tag--orange' : 'tag--muted'">{{ approval.status === 'PENDING_APPROVAL' ? '待审批' : approval.status === 'EXECUTED' ? '已执行' : approval.status === 'STALE' ? '版本已失效' : approval.status === 'REJECTED' ? '已驳回' : '已通过' }}</span>
          <b>{{ actionLabels[approval.action] }}</b><span><template v-if="approval.targetIamUserId">目标处理人：{{ approval.targetIamUserId }} · </template>{{ approval.reason }}</span>
          <small>申请：{{ approval.applicantIamUserId }} · {{ formatFullTime(approval.createdAt) }} · 流程 {{ approval.processKey }} v{{ approval.processVersion }}</small>
          <small v-if="approval.approverIamUserId">审批：{{ approval.approverIamUserId }}<template v-if="approval.decidedAt"> · {{ formatFullTime(approval.decidedAt) }}</template><template v-if="approval.decisionReason"> · {{ approval.decisionReason }}</template></small>
          <small v-if="approval.status === 'APPROVED'">审批已完成，正在等待服务端受控执行。</small>
          <small v-if="approval.executedAt">服务端执行：{{ formatFullTime(approval.executedAt) }}</small>
          <div v-if="approval.status === 'PENDING_APPROVAL'" class="modal-actions"><button class="button button--primary button--compact" :disabled="lifecycleApprovalSubmitting === approval.id" type="button" @click="decideLifecycleApproval(approval.id, 'APPROVED')">同意</button><button class="button button--secondary button--compact" :disabled="lifecycleApprovalSubmitting === approval.id" type="button" @click="decideLifecycleApproval(approval.id, 'REJECTED')">驳回</button></div>
        </div></div>
      </section>
</div>
        <div v-show="activeDetailTab === 'collaboration'" class="processing-main-content">      <section v-if="handoverRequests.length" class="panel detail-panel collaboration-sidebar-panel collaboration-handover-panel">
        <div class="panel-header"><div><h3>交接班确认</h3><p>交接不会立即改写主办人；仅指定接班人在 Flowable 任务中确认后才生效。</p></div></div>
        <p v-if="handoverError" class="form-alert form-alert--error">{{ handoverError }}</p>
        <div class="related-ticket-list">
          <div v-for="handover in handoverRequests" :key="handover.id" class="related-ticket-row">
            <span class="tag" :class="handover.status === 'ACCEPTED' ? 'tag--green' : handover.status === 'PENDING_CONFIRMATION' ? 'tag--orange' : 'tag--muted'">{{ handover.status === 'PENDING_CONFIRMATION' ? '待接班确认' : handover.status === 'ACCEPTED' ? '已确认' : handover.status === 'REJECTED' ? '已拒绝' : '已失效' }}</span>
            <b>交接班</b><span>接班人：{{ handover.targetIamUserId }} · {{ handover.reason }}</span>
            <small>发起：{{ handover.applicantIamUserId }} · {{ formatFullTime(handover.createdAt) }} · 流程版本 v{{ handover.processDefinitionVersion }}</small>
            <small v-if="handover.decidedAt">确认：{{ formatFullTime(handover.decidedAt) }}<template v-if="handover.decisionReason"> · {{ handover.decisionReason }}</template></small>
            <div v-if="handover.status === 'PENDING_CONFIRMATION'" class="modal-actions"><button class="button button--primary button--compact" type="button" :disabled="handoverSubmitting === handover.id" @click="openHandoverDecision(handover, 'ACCEPTED')">确认接班</button><button class="button button--secondary button--compact" type="button" :disabled="handoverSubmitting === handover.id" @click="openHandoverDecision(handover, 'REJECTED')">拒绝交接</button></div>
          </div>
        </div>
      </section>
      <section v-if="coHandlerRequests.length" class="panel detail-panel collaboration-sidebar-panel collaboration-cohandler-panel">
        <div class="panel-header"><div><h3>协办确认</h3><p>加签不会立即授予协办权限；仅候选协办人在 Flowable 任务中确认后生效。</p></div></div>
        <p v-if="coHandlerError" class="form-alert form-alert--error">{{ coHandlerError }}</p>
        <div class="related-ticket-list"><div v-for="request in coHandlerRequests" :key="request.id" class="related-ticket-row">
          <span class="tag" :class="request.status === 'ACCEPTED' ? 'tag--green' : request.status === 'PENDING_CONFIRMATION' ? 'tag--orange' : 'tag--muted'">{{ request.status === 'PENDING_CONFIRMATION' ? '待协办确认' : request.status === 'ACCEPTED' ? '已确认' : request.status === 'REJECTED' ? '已拒绝' : '已失效' }}</span>
          <b>添加协办</b><span>候选协办：{{ request.targetIamUserId }} · {{ request.reason }}</span>
          <small>发起：{{ request.applicantIamUserId }} · {{ formatFullTime(request.createdAt) }} · 流程版本 v{{ request.processDefinitionVersion }}</small>
          <small v-if="request.decidedAt">确认：{{ formatFullTime(request.decidedAt) }}<template v-if="request.decisionReason"> · {{ request.decisionReason }}</template></small>
          <div v-if="request.status === 'PENDING_CONFIRMATION'" class="modal-actions"><button class="button button--primary button--compact" type="button" :disabled="coHandlerSubmitting === request.id" @click="openCoHandlerDecision(request, 'ACCEPTED')">确认协办</button><button class="button button--secondary button--compact" type="button" :disabled="coHandlerSubmitting === request.id" @click="openCoHandlerDecision(request, 'REJECTED')">拒绝协办</button></div>
        </div></div>
      </section>
<p v-if="!handoverRequests.length && !coHandlerRequests.length" class="workflow-unavailable">暂无交接或协办确认记录。</p></div>
        <div v-show="activeDetailTab === 'history'" class="processing-main-content">      <section class="panel detail-panel"><div class="panel-header"><div><h3>流程与审批记录</h3><p>流程动作、审批决策和系统事件统一留痕；当前无审批决策时不虚构审批记录。</p></div></div><ol class="ticket-timeline"><li v-for="item in timeline" :key="item.id"><span></span><div><b>{{ item.label }}</b><small>{{ formatFullTime(item.occurredAt) }}<template v-if="item.actor"> · {{ item.actor.displayName }}</template></small><p v-if="item.note">{{ item.note }}</p><em v-if="item.auditEventId">审计：{{ item.auditEventId }}</em></div></li></ol></section>
</div>
      </section>
      <aside class="processing-sidebar" aria-label="工单时效与协作参考" :inert="Boolean(selectedAction)">
      <div class="processing-summary"><section class="panel detail-panel"><div class="panel-header"><div><h3>处理摘要</h3><p>当前状态、主办人和组织以服务端为准。</p></div></div><dl class="detail-definition"><div><dt>当前状态</dt><dd>{{ statusNames[ticket.status] }}</dd></div><div><dt>当前处理人</dt><dd>{{ currentAssignee ? currentAssigneeLabel : acceptanceCandidateCount ? `等待 ${acceptanceCandidateCount} 人抢单` : '待后端分派' }}</dd></div><div><dt>处理组织</dt><dd>{{ currentAssignee?.organizationName ?? '—' }}</dd></div></dl><div v-if="acceptanceCandidates.length" class="acceptance-candidates acceptance-candidates--summary"><div class="acceptance-candidates__heading"><b>可抢单人员（{{ acceptanceCandidateCount }}）</b></div><ul class="acceptance-candidate-list"><li v-for="candidate in acceptanceCandidates" :key="candidate.iamUserId" :title="[candidate.organizationName, candidate.positionName].filter(Boolean).join(' · ')"><span class="participant-avatar">{{ candidate.displayName.slice(0, 1) }}</span><span><b>{{ candidate.displayName }}</b></span></li></ul></div></section><section class="panel detail-panel sla-ticket-panel"><div class="panel-header"><div><h3>SLA 时效</h3><p>目标、暂停和风险由服务端计算。</p></div></div><template v-if="ticket.sla"><div class="sla-ticket-policy"><b>{{ ticket.sla.policyName }}</b><span class="tag" :class="ticket.sla.riskLevel === 'NORMAL' ? 'tag--green' : ticket.sla.riskLevel === 'AT_RISK' ? 'tag--orange' : 'tag--red'">{{ slaRiskLabel(ticket.sla.riskLevel) }}</span></div><dl class="detail-definition"><div><dt>响应目标</dt><dd>{{ ticket.sla.responseTargetAt ? formatFullTime(ticket.sla.responseTargetAt) : '—' }}<small>{{ slaRemaining(ticket.sla.responseRemainingMinutes) }}</small></dd></div><div><dt>解决目标</dt><dd>{{ ticket.sla.resolutionTargetAt ? formatFullTime(ticket.sla.resolutionTargetAt) : '—' }}<small>{{ slaRemaining(ticket.sla.resolutionRemainingMinutes) }}</small></dd></div><div><dt>计时状态</dt><dd>{{ ticket.sla.paused ? '已暂停（已审批）' : '计时中' }}<small v-if="ticket.sla.pausedMinutes">累计暂停 {{ ticket.sla.pausedMinutes }} 分钟</small></dd></div></dl></template><p v-else class="workflow-unavailable">当前未返回可查看的 SLA 明细。</p></section></div>
        <TicketKnowledgeSidebar :key="ticket.id" :ticket-id="ticket.id" :catalog-id="ticket.serviceCatalogItem.id" @reference="referenceKnowledge" />
      <section class="panel detail-panel collaboration-sidebar-panel collaboration-participants-panel"><div class="panel-header"><div><h3>主办与协办</h3><p>主办/协办关系由服务端协作规则返回。</p></div><button v-if="collaborationActions.some((item) => item.code === 'ADD_COHANDLER')" class="button button--secondary button--compact" type="button" @click="openAction(collaborationActions.find((item) => item.code === 'ADD_COHANDLER')!)">添加协办</button></div><ul v-if="participants.length" class="participant-list"><li v-for="participant in participants" :key="`${participant.role}-${participant.identity.iamUserId}`"><span class="participant-avatar">{{ participant.identity.displayName.slice(0, 1) }}</span><div><b>{{ participant.identity.displayName }}</b><small>{{ participant.identity.organizationName }} · {{ participant.identity.positionName ?? '—' }}</small></div><span class="role-pill">{{ participantRole(participant.role) }}</span></li></ul><p v-else class="workflow-unavailable">暂未返回协作人员。</p></section>
      <section class="panel detail-panel collaboration-sidebar-panel collaboration-comments-panel"><div class="panel-header"><div><h3>内部沟通</h3><p>仅对具备内部协作权限的当前人员展示。</p></div><button v-if="canComment" class="button button--secondary button--compact" type="button" @click="openAction({ code: 'INTERNAL_COMMENT', label: '内部评论' })">发表评论</button></div><div v-if="comments.length" class="comment-list"><article v-for="comment in comments" :key="comment.id"><div class="comment-head"><b>{{ comment.author.displayName }}</b><span>{{ formatFullTime(comment.createdAt) }}</span></div><p>{{ comment.content }}</p><small v-if="comment.auditEventId">内部 · 审计：{{ comment.auditEventId }}</small></article></div><p v-else class="workflow-unavailable">暂无可见内部评论，或当前身份无查看权限。</p></section>
      </aside>
    </div>
    <footer class="processing-actionbar" aria-label="当前可执行工单操作">
      <div><span>当前：{{ statusNames[ticket.status] }}</span><small>操作结果以服务端流程与审批为准</small></div>
      <div class="processing-action-buttons">
        <button v-if="processingDetailsEditable" class="button button--secondary button--compact" type="button" :disabled="busy || source === 'demo' || !processingDirty" @click="saveProcessingDetails">{{ processingDetailsSaving ? '保存中…' : '保存' }}</button>
        <button v-for="action in footerActions" :key="action.code" class="button button--compact" :class="['RESOLVE', 'ACCEPT', 'CLAIM', 'REQUEST_USER_FEEDBACK', 'CLOSE'].includes(action.code) ? 'button--primary' : 'button--secondary'" type="button" :disabled="busy || source === 'demo' || Boolean(action.disabledReason)" :title="action.disabledReason" @click="openAction(action)">{{ action.label ?? actionLabels[action.code] }}</button>
        <small v-if="!footerActions.length && !processingDetailsEditable">当前没有可执行操作，请查看流程记录或联系处理人。</small>
      </div>
    </footer>
<div v-if="selectedAction" class="modal-backdrop" @mousedown.self="closeAction"><section class="action-modal" role="dialog" aria-modal="true" :aria-label="actionTitle" @keydown="onActionKeydown"><div class="modal-heading"><div><span class="eyebrow">工单 {{ ticket.id }} · 版本 v{{ ticket.version }}</span><h3>{{ actionTitle }}</h3><p>{{ actionHelp(selectedAction.code) }}</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeAction">×</button></div><form class="action-form" @submit.prevent="submitAction"><label v-if="selectedAction.code === 'CONTROLLED_JUMP_REQUEST'" class="field"><span>目标流程节点 <b>*</b></span><select v-model="actionForm.targetNode"><option value="">请选择允许的节点</option><option v-for="node in processNodes" :key="node.code" :value="node.code">{{ node.label }}</option></select></label><p v-if="!isComment && !requiresReason" class="workflow-unavailable">此动作不修改处理信息；如有未保存内容，请先点击底部“保存”。</p><label v-if="needsTarget" class="field"><span>下一处理人 <b>*</b></span><select v-model="actionForm.targetIamUserId" :disabled="!nextHandlerCandidates.length"><option value="">{{ nextHandlerCandidates.length ? '请选择已授权人员' : '暂无可选人员' }}</option><option v-for="candidate in nextHandlerCandidates" :key="candidate.iamUserId" :value="candidate.iamUserId">{{ candidate.displayName }} · {{ candidate.organizationName }}</option></select><small>候选名单由服务端按当前系统、目录、人员状态、角色和数据范围过滤；不接受手填账号。</small></label><label v-if="requiresReason" class="field"><span>操作理由 <b>*</b></span><textarea v-model="actionForm.reason" maxlength="1000" rows="4" placeholder="填写本次操作依据；主表单处理或解决说明已自动带入。" /></label><label v-if="isComment || requiresReason" class="field"><span>{{ isComment ? '评论内容' : '处理补充' }}<b v-if="isComment">*</b></span><textarea v-model.trim="actionForm.detail" :maxlength="isComment ? 2000 : 1000" rows="4" :placeholder="isComment ? '仅填写可供内部协作的处理信息，不填写密码、令牌等敏感数据。' : '可选：补充说明将与操作理由一起提交（合计最多1000字符）。'" /></label><div class="action-form__meta"><span>乐观锁版本</span><b>v{{ actionContext?.version }}</b><small>提交时由后端校验；版本冲突需刷新后重试。</small></div><p v-if="actionError" class="form-alert form-alert--error">{{ actionError }}</p><p v-if="actionNotice" class="form-alert form-alert--success">{{ actionNotice }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="actionSubmitting" @click="closeAction">取消</button><button class="button button--primary" type="submit" :disabled="actionSubmitting || (needsTarget && !nextHandlerCandidates.length)">{{ source === 'demo' ? '演示中禁止提交' : actionSubmitting ? '正在提交…' : selectedAction.code === 'CLAIM' ? '确认抢单' : (actionTitle.startsWith('确认') ? actionTitle : `确认${actionTitle}`) }}</button></div></form></section></div>
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
    <div v-if="selectedHandoverDecision" class="modal-backdrop" @mousedown.self="!handoverSubmitting && (selectedHandoverDecision = null)"><section class="action-modal" role="dialog" aria-modal="true" aria-label="交接班确认"><div class="modal-heading"><div><span class="eyebrow">Flowable 交接确认任务</span><h3>{{ selectedHandoverDecision.decision === 'ACCEPTED' ? '确认接班' : '拒绝交接' }}</h3><p>仅任务指定接班人可提交；服务端会再次校验当前身份、任务归属和流程版本。</p></div><button class="modal-close" type="button" :disabled="Boolean(handoverSubmitting)" @click="selectedHandoverDecision = null">×</button></div><form class="action-form" @submit.prevent="decideHandover"><label class="field"><span>确认说明 <b>*</b></span><select v-model="handoverReason"><option value="">请选择标准说明</option><option value="已核对当前处理记录并确认接班">已核对当前处理记录并确认接班</option><option value="当前工作负载无法承接该工单">当前工作负载无法承接该工单</option><option value="交接信息不完整，需要补充后再处理">交接信息不完整，需要补充后再处理</option></select></label><p v-if="handoverError" class="form-alert form-alert--error">{{ handoverError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="Boolean(handoverSubmitting)" @click="selectedHandoverDecision = null">取消</button><button class="button button--primary" type="submit" :disabled="Boolean(handoverSubmitting)">{{ handoverSubmitting ? '正在提交…' : '提交确认' }}</button></div></form></section></div>
    <div v-if="selectedCoHandlerDecision" class="modal-backdrop" @mousedown.self="!coHandlerSubmitting && (selectedCoHandlerDecision = null)"><section class="action-modal" role="dialog" aria-modal="true" aria-label="协办确认"><div class="modal-heading"><div><span class="eyebrow">Flowable 协办确认任务</span><h3>{{ selectedCoHandlerDecision.decision === 'ACCEPTED' ? '确认协办' : '拒绝协办' }}</h3><p>仅任务指定的候选协办可确认；拒绝或来源版本失效都不会授予协办权限。</p></div><button class="modal-close" type="button" :disabled="Boolean(coHandlerSubmitting)" @click="selectedCoHandlerDecision = null">×</button></div><form class="action-form" @submit.prevent="decideCoHandler"><label class="field"><span>确认说明 <b>*</b></span><select v-model="coHandlerReason"><option value="">请选择标准说明</option><option value="已核对处理记录并确认协办">已核对处理记录并确认协办</option><option value="当前工作负载无法承接协办任务">当前工作负载无法承接协办任务</option><option value="协作范围不清晰，需要补充后再确认">协作范围不清晰，需要补充后再确认</option></select></label><p v-if="coHandlerError" class="form-alert form-alert--error">{{ coHandlerError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="Boolean(coHandlerSubmitting)" @click="selectedCoHandlerDecision = null">取消</button><button class="button button--primary" type="submit" :disabled="Boolean(coHandlerSubmitting)">{{ coHandlerSubmitting ? '正在提交…' : '提交确认' }}</button></div></form></section></div>
    <div v-if="showRelation" class="modal-backdrop" @mousedown.self="closeRelation"><section class="action-modal" role="dialog" aria-modal="true" aria-label="关联工单"><div class="modal-heading"><div><span class="eyebrow">工单关系</span><h3>关联工单</h3><p>服务端会检查本单可修改权限及目标工单可读权限，不因编号返回而泄露越权工单。</p></div><button class="modal-close" type="button" aria-label="关闭" @click="closeRelation">×</button></div><form class="action-form" @submit.prevent="submitRelation"><label class="field"><span>关系类型 <b>*</b></span><select v-model="relationForm.relationType"><option value="RELATED">关联工单</option><option value="DUPLICATE_OF">本单重复于目标单</option><option value="PARENT_OF">本单为目标单父工单</option><option value="PROBLEM_REFERENCE">关联问题单</option><option value="CHANGE_REFERENCE">关联变更单</option></select></label><label class="field"><span>目标工单编号 <b>*</b></span><input v-model.trim="relationForm.targetTicketId" maxlength="24" placeholder="例如 TKT-20260822-000001" /><small>仅接受完整编号；不支持按标题搜索或任意跨组织枚举。</small></label><p v-if="relationError" class="form-alert form-alert--error">{{ relationError }}</p><div class="modal-actions"><button class="button button--secondary" type="button" :disabled="relationSubmitting" @click="closeRelation">取消</button><button class="button button--primary" type="submit" :disabled="relationSubmitting">{{ relationSubmitting ? '关联中…' : '创建受控关联' }}</button></div></form></section></div>
  </template>
  </section>
</template>
