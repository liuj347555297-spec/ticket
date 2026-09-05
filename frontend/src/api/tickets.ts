import { ApiError, apiRequest, apiUpload } from '@/api/client'
import { demoTicketRepository } from '@/api/demo-tickets'
import { workflowOutcome } from '@/utils/workflowOutcome'

export type TicketType = 'INCIDENT' | 'SERVICE_REQUEST' | 'ACCESS_REQUEST' | 'PROBLEM' | 'CHANGE'
export type TicketStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_CLASSIFICATION'
  | 'PENDING_ASSIGNMENT'
  | 'PENDING_ACCEPTANCE'
  | 'IN_PROGRESS'
  | 'RESOLVED'
  | 'PENDING_USER_FEEDBACK'
  | 'CLOSED'
  | 'CANCELLED'
  | 'ON_HOLD'
export type TicketPriority = 'P1' | 'P2' | 'P3' | 'P4'
export type TicketDescriptionFormat = 'PLAIN_TEXT' | 'RICH_TEXT'
export type TicketQueue = 'ALL' | 'MY_TODO' | 'OVERDUE' | 'TODAY_DUE' | 'TODAY_COMPLETED' | 'MY_DONE' | 'MY_REQUESTED' | 'DRAFTS' | 'TO_READ'
export type TagKind = 'STANDARD' | 'FREE'
export type TicketRelationType = 'RELATED' | 'DUPLICATE_OF' | 'PARENT_OF' | 'PROBLEM_REFERENCE' | 'CHANGE_REFERENCE'
/** Values are the server's WorkflowAction enum, not client-defined state transitions. */
export type TicketLifecycleAction = 'CLASSIFY' | 'ASSIGN' | 'ACCEPT' | 'START_PROCESSING' | 'REQUEST_USER_FEEDBACK' | 'RESOLVE' | 'CLOSE' | 'REOPEN' | 'CANCEL' | 'HOLD' | 'RESUME' | 'ESCALATE'
export type TicketWorkAction = 'TRANSFER' | 'ADD_COHANDLER' | 'CLAIM' | 'HANDOVER'
export type TicketActionCode = TicketLifecycleAction | TicketWorkAction | 'INTERNAL_COMMENT' | 'CONTROLLED_JUMP_REQUEST'

/** Returned by the server's workflow read model. The UI must never infer this from a status. */
export interface TicketAvailableAction {
  code: TicketActionCode
  label?: string
  requiresTarget?: boolean
  disabledReason?: string
}

export interface TicketParticipant {
  role: 'PRIMARY' | 'COLLABORATOR'
  identity: IdentitySnapshot
  assignedAt: string
}

/** Minimal active-directory projection returned only for an unclaimed shared queue task. */
export interface AcceptanceCandidate {
  iamUserId: string
  displayName: string
  organizationName?: string
  positionName?: string
}

export interface TicketTimelineEvent {
  id: string
  label: string
  occurredAt: string
  note?: string
  actor?: IdentitySnapshot
  auditEventId?: string
}

export interface TicketComment {
  id: string
  visibility: 'INTERNAL'
  author: IdentitySnapshot
  content: string
  createdAt: string
  auditEventId?: string
}

export interface TicketTag {
  name: string
  kind: TagKind
}

export type AttachmentScanState = 'RECEIVED' | 'SCANNING' | 'SCAN_PASSED' | 'QUARANTINED' | 'REJECTED' | 'SCAN_UNAVAILABLE'
export interface TicketAttachment {
  id: string
  displayFileName: string
  detectedMediaType: string
  sizeBytes: number
  scanState: AttachmentScanState
  /** Download remains a server-authorized operation even when this read model is true. */
  downloadable?: boolean
  retentionState?: 'ACTIVE' | 'DELETE_REQUESTED' | 'RETAINED'
}
export interface TicketAttachmentPage { items: TicketAttachment[]; total: number }

/** Read-only diagram data generated from the current platform-owned Flowable ticket lifecycle. */
export interface TicketLifecyclePreview {
  processKey: string
  processDefinitionId: string
  name: string
  version: number
  nodes: Array<{ id: string; label: string; type: 'START' | 'END' | 'USER_TASK' | 'GATEWAY' | 'ACTIVITY' }>
  flows: Array<{ id: string; sourceNodeId: string; targetNodeId: string }>
}

interface TicketAttachmentWire {
  id: string
  filename: string
  detectedMediaType: string
  sizeBytes: number
  scanStatus: 'QUARANTINED' | 'CLEAN' | 'REJECTED' | 'SCAN_FAILED'
}

interface TicketAttachmentUploadWire extends TicketAttachmentWire { createdAt: string }

interface WorkflowCommentWire {
  id: string
  authorIamUserId: string
  body: string
  createdAt: string
}

interface WorkflowOverviewWire {
  comments: WorkflowCommentWire[]
  availableActions: TicketAvailableAction[]
  instance: {
    currentNode: string
    primaryAssigneeIamUserId?: string
    escalationLevel: number
  }
  tasks: Array<{
    id: string
    nodeKey: string
    status: 'OPEN' | 'CLAIMED' | 'COMPLETED' | 'CANCELLED'
    candidateRole?: string
    candidateIamUserId?: string
    assigneeIamUserId?: string
    createdAt: string
    updatedAt: string
  }>
  events: Array<{
    id: number
    action: string
    actorIamUserId: string
    requestId: string
    occurredAt: string
  }>
  participants: Array<{
    role: 'PRIMARY' | 'CO_HANDLER'
    identity: IdentitySnapshot
    assignedAt: string
  }>
  acceptanceCandidates?: AcceptanceCandidate[]
  candidateCount?: number
  approvalRequests: Array<{
    id: string
    applicantIamUserId: string
    sourceNode: string
    targetNode: string
    reason: string
    status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTING' | 'EXECUTED' | 'CANCELLED'
    createdAt: string
    approverIamUserId?: string
    decidedAt?: string
    executorIamUserId?: string
    executionStartedAt?: string
    executedAt?: string
    executedFromNode?: string
    executedToNode?: string
    executionFailureReason?: string
    approvalPolicy: {
      processKey: string
      processDefinitionId: string
      processVersion: number
      candidateRoles: string[]
      /** Frozen IAM candidate IDs are retained only in the server-side audit snapshot. */
      decisionMode: string
      timeoutPolicyVersion: string
      escalationPolicyVersion: string
      capturedAt: string
    }
  }>
  approvalDecisions: Array<{
    id: string
    approvalRequestId: string
    engineTaskId?: string
    approverIamUserId: string
    decision: 'APPROVED' | 'REJECTED'
    reason: string
    decidedAt: string
  }>
  /** A handover is pending until its server-assigned recipient completes the Flowable confirmation task. */
  handoverRequests: Array<{
    id: string
    applicantIamUserId: string
    targetIamUserId: string
    reason: string
    status: 'PENDING_CONFIRMATION' | 'ACCEPTED' | 'REJECTED' | 'STALE'
    processDefinitionId: string
    processDefinitionVersion: number
    createdAt: string
    decidedAt?: string
    decisionReason?: string
  }>
  /** A co-handler is only added after the target completes its assigned Flowable confirmation task. */
  coHandlerRequests: Array<{
    id: string
    applicantIamUserId: string
    targetIamUserId: string
    reason: string
    status: 'PENDING_CONFIRMATION' | 'ACCEPTED' | 'REJECTED' | 'STALE'
    processDefinitionId: string
    processDefinitionVersion: number
    createdAt: string
    decidedAt?: string
    decisionReason?: string
  }>
  /** High-risk state changes have their own Flowable approval aggregate. */
  lifecycleApprovalRequests: Array<{
    id: string
    action: 'HOLD' | 'ESCALATE' | 'CANCEL' | 'REOPEN' | 'ASSIGN' | 'ACCEPT' | 'RESOLVE' | 'CLOSE'
    applicantIamUserId: string
    reason: string
    /** Present only for ASSIGN and frozen by the server before the approval task exists. */
    targetIamUserId?: string
    sourceTicketVersion: number
    sourceWorkflowVersion: number
    processKey: string
    processDefinitionId: string
    processVersion: number
    status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXECUTED' | 'STALE'
    approverIamUserId?: string
    decisionReason?: string
    decidedAt?: string
    executedAt?: string
    createdAt: string
  }>
  assignmentSnapshots?: Array<{ nodeKey: string; mode: 'SYSTEM_RANDOM' | 'PREVIOUS_HANDLER_SELECTS' | 'SHARED_QUEUE'; candidateRoles: string[]; policyVersion: number; selectedIamUserId?: string; capturedAt: string }>
  /** Returned only to service managers/platform administrators. Never inferred by the browser. */
  controlledJumpActions: Array<{
    requestId: string
    canPreflight: boolean
    canExecute: boolean
    disabledReason?: string
  }>
}

export interface ControlledJumpPreflight {
  executable: boolean
  blockingReasons: string[]
  currentTaskDisposition: string
  targetCandidateRole: string
  candidateResolution: string
  candidateRecalculationRequired: boolean
  slaImpact: string
  notificationImpact: string
}

/** A manager's worklist row is sourced from a live Flowable candidate task and an authorized ticket projection. */
export interface ApprovalTaskInboxItem {
  taskType: 'CONTROLLED_JUMP' | 'LIFECYCLE_ACTION' | 'HANDOVER_CONFIRMATION' | 'COHANDLER_CONFIRMATION'
  requestId: string
  ticketId: string
  ticketTitle: string
  ticketType: TicketType
  ticketStatus: TicketStatus
  ticketPriority: TicketPriority
  serviceCatalogItem: ServiceCatalogSummary
  requester: IdentitySnapshot
  actionCode: string
  summary: string
  requestedAt: string
  engineTaskCreatedAt: string
}

export interface ApprovalTaskInbox {
  items: ApprovalTaskInboxItem[]
  page: number
  pageSize: number
}

export type SlaRiskLevel = 'NORMAL' | 'AT_RISK' | 'BREACHED'

/**
 * SLA is a server-calculated read model. It is omitted entirely when the current
 * identity is not allowed to view the ticket or its SLA details.
 */
export interface TicketSla {
  policyName: string
  responseTargetAt?: string
  resolutionTargetAt?: string
  responseRemainingMinutes?: number
  resolutionRemainingMinutes?: number
  paused: boolean
  pausedAt?: string
  pausedMinutes?: number
  riskLevel: SlaRiskLevel
  calculatedAt: string
}

/** Wire model for GET /tickets/{ticketId}/sla. Never accept this model from a write form. */
export interface TicketSlaStatusResponse {
  ticketId: string
  policySnapshot: { policyId: string; policyVersion: number; workCalendarId: string; workCalendarVersion: number }
  calculationTimeZone: string
  calculatedAt: string
  paused: boolean
  pauseReasonCode?: string
  targets: Array<{
    targetType: 'FIRST_RESPONSE' | 'RESOLUTION'
    state: 'ON_TRACK' | 'AT_RISK' | 'BREACHED' | 'PAUSED' | 'NOT_APPLICABLE'
    targetAt: string
    businessMinutesRemaining: number
    breachedAt?: string
  }>
}

/** Temporary compatibility read model for the first Spring Boot SLA implementation. */
export interface LegacyTicketSlaStatusResponse {
  ticketId: string
  policyId: string
  policyNameSnapshot: string
  calendarKeySnapshot: string
  responseDueAt: string
  resolutionDueAt: string
  pausedSeconds: number
  pauseStartedAt?: string
  riskLevel: 'ON_TRACK' | 'AT_RISK' | 'BREACHED'
  calculatedAt: string
}
export type TicketSlaResponse = TicketSlaStatusResponse | LegacyTicketSlaStatusResponse

export interface IdentitySnapshot {
  iamUserId: string
  displayName: string
  organizationName: string
  positionName?: string
  capturedAt: string
}

export interface ServiceCatalogSummary {
  id: string
  name: string
}

/** Related-ticket summaries are returned only after both endpoints pass object authorization. */
export interface TicketRelation {
  relationType: TicketRelationType
  direction: 'OUTBOUND' | 'INBOUND'
  relatedTicket: Pick<Ticket, 'id' | 'type' | 'status' | 'priority' | 'title'>
  createdByIamUserId: string
  createdAt: string
}

export interface Ticket {
  id: string
  type: TicketType
  status: TicketStatus
  priority: TicketPriority
  title: string
  description?: string
  /** Only present when the server stored a rich-text body after allow-list sanitization. */
  descriptionFormat?: TicketDescriptionFormat
  descriptionHtml?: string
  requester: IdentitySnapshot
  assignee?: IdentitySnapshot
  serviceCatalogItem: ServiceCatalogSummary
  tags?: TicketTag[]
  createdAt: string
  updatedAt?: string
  version: number
  /** Optional until the workflow read model is available on every backend profile. */
  availableActions?: TicketAvailableAction[]
  participants?: TicketParticipant[]
  timeline?: TicketTimelineEvent[]
  attachments?: TicketAttachment[]
  sla?: TicketSla
}

export interface TicketPage {
  items: Ticket[]
  page: number
  pageSize: number
  total: number
  /** Opaque server cursor bound to the current identity, filters, sort and snapshot. */
  nextCursor?: string
  /** Prefer this server decision over deriving continuation from item counts. */
  hasMore?: boolean
  /** Server-side query snapshot time used to explain stable pagination to the operator. */
  snapshotAt?: string
}

export interface TicketCreateRequest {
  serviceCatalogItemId: string
  serviceCatalogFormVersion: number
  /** Server-authorized service-system registry selection; never inferred from free text. */
  serviceSystemCode?: string
  /** Optional module within the selected service system. */
  serviceSystemModuleCode?: string
  type: TicketType
  title: string
  description: string
  descriptionFormat?: TicketDescriptionFormat
  structuredFields: Record<string, string | boolean | string[]>
  tags?: TicketTag[]
  relatedConfigurationItemIds?: string[]
}

export interface TicketQuery {
  page?: number
  pageSize?: number
  cursor?: string
  status?: TicketStatus
  type?: TicketType
  priority?: TicketPriority
  q?: string
  queue?: TicketQueue
  serviceCatalog?: string
  requesterOrganization?: string
  createdFrom?: string
  createdTo?: string
}

export interface TicketResult<T> {
  data: T
  source: 'api' | 'demo'
}

export interface TicketActionRequest {
  action: TicketActionCode
  version: number
  targetIamUserId?: string
  comment?: string
  reason?: string
  targetNode?: 'classify' | 'assign' | 'accept' | 'processing' | 'user_feedback' | 'closure'
}

export interface TicketActionResult {
  ticket: Ticket
  decision: { outcome: 'COMPLETED' | 'PENDING_APPROVAL' | 'PENDING_PROCESS_TASK'; workflowInstanceId: string; currentNodeCode: string; auditEventId: string }
  slaImpact: { calculatedByServer: true; impact: 'NONE' | 'PAUSED' | 'RESUMED' | 'RECALCULATED' | 'BREACH_RISK'; targetAt?: string }
}
export interface NextHandlerCandidate { iamUserId: string; displayName: string; organizationName: string }

export interface TicketProcessingDetails {
  ticketId: string
  eventSource?: 'PHONE' | 'EMAIL' | 'MONITORING_ALERT' | 'ON_SITE_FEEDBACK' | 'OTHER'
  proposingOrganization?: string
  onSiteSupportRequired?: boolean
  causeCategory?: 'HARDWARE' | 'SOFTWARE_DEFECT' | 'CONFIGURATION' | 'NETWORK' | 'ACCESS_CONTROL' | 'DATA' | 'USER_OPERATION' | 'EXTERNAL_DEPENDENCY' | 'UNDER_INVESTIGATION'
  processingDescription?: string
  resolutionDescription?: string
  thirdPartyHandled?: boolean
  currentProgress?: string
  version: number
  updatedByIamUserId?: string
  updatedAt?: string
  /** Display hint only. PUT performs ticket and current-primary-handler authorization again. */
  editable: boolean
}

export type TicketProcessingDetailsInput = Pick<TicketProcessingDetails,
  'eventSource' | 'proposingOrganization' | 'onSiteSupportRequired' | 'causeCategory' |
  'processingDescription' | 'resolutionDescription' | 'thirdPartyHandled' | 'currentProgress'>

export interface TicketWorkActionResult extends Omit<TicketActionResult, 'slaImpact'> {
  participants: TicketParticipant[]
}

function isConnectionFailure(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && error.status === 503)
}

function createQuery(query: TicketQuery): string {
  const params = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const search = params.toString()
  return search ? `?${search}` : ''
}

function newIdempotencyKey(): string {
  return crypto.randomUUID()
}

/**
 * The demo repository is an isolated, in-memory visual fallback. It is only used
 * for connection failures in development, never to derive identity or authorization.
 */
const canUseDemoFallback = import.meta.env.DEV && import.meta.env.VITE_DEMO_MODE !== 'false'

export const ticketApi = {
  async getTicketLifecyclePreview(): Promise<TicketLifecyclePreview> {
    return apiRequest<TicketLifecyclePreview>('/workflow/ticket-lifecycle/preview')
  },

  async list(query: TicketQuery = {}): Promise<TicketResult<TicketPage>> {
    try {
      return { data: await apiRequest<TicketPage>(`/tickets${createQuery(query)}`), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoTicketRepository.list(query), source: 'demo' }
    }
  },

  async get(ticketId: string): Promise<TicketResult<Ticket>> {
    try {
      return { data: await apiRequest<Ticket>(`/tickets/${encodeURIComponent(ticketId)}`), source: 'api' }
    } catch (error) {
      if (!canUseDemoFallback || !isConnectionFailure(error)) throw error
      return { data: demoTicketRepository.get(ticketId), source: 'demo' }
    }
  },

  async create(request: TicketCreateRequest, idempotencyKey = newIdempotencyKey()): Promise<TicketResult<Ticket>> {
      return {
        data: await apiRequest<Ticket>('/tickets', {
          method: 'POST',
          headers: { 'Idempotency-Key': idempotencyKey },
          body: request,
        }),
        source: 'api',
      }
    // A failed mutation may already have committed. Never turn it into a demo success.
  },

  async uploadAttachment(ticketId: string, file: File): Promise<TicketAttachmentUploadWire> {
    const body = new FormData()
    body.set('file', file, file.name)
    return apiUpload<TicketAttachmentUploadWire>(`/tickets/${encodeURIComponent(ticketId)}/attachments`, body)
  },

  async updateDescription(ticketId: string, version: number, description: string): Promise<Ticket> {
    return apiRequest<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/description`, {
      method: 'PATCH', headers: { 'If-Match': `"${version}"` }, body: { description, descriptionFormat: 'RICH_TEXT' },
    })
  },

  async executeAction(ticketId: string, request: TicketActionRequest): Promise<TicketActionResult> {
    // version is an HTTP optimistic-lock precondition, not a WorkflowActionRequest field. Sending
    // it in JSON violates the backend's fail-closed unknown-field policy and yields INVALID_REQUEST.
    const { version, ...command } = request
    const ticket = await apiRequest<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/workflow/actions`, {
      method: 'POST',
      // If-Match is an HTTP entity-tag. Real servlet containers reject an unquoted number.
      headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': newIdempotencyKey() },
      body: command,
    })
    return {
      ticket,
      decision: { outcome: workflowOutcome(request.action, ticket.status), workflowInstanceId: 'server-managed', currentNodeCode: ticket.status, auditEventId: 'server-managed' },
      slaImpact: { calculatedByServer: true, impact: 'RECALCULATED' },
    }
  },

  async getWorkflowOverview(ticketId: string): Promise<WorkflowOverviewWire> {
    return apiRequest<WorkflowOverviewWire>(`/tickets/${encodeURIComponent(ticketId)}/workflow`)
  },
  async getNextHandlerCandidates(ticketId: string): Promise<NextHandlerCandidate[]> { return apiRequest<NextHandlerCandidate[]>(`/tickets/${encodeURIComponent(ticketId)}/workflow/next-handler-candidates?targetNode=processing`) },
  async getTransferCandidates(ticketId: string): Promise<NextHandlerCandidate[]> { return apiRequest<NextHandlerCandidate[]>(`/tickets/${encodeURIComponent(ticketId)}/workflow/transfer-candidates`) },
  async getAssignmentCandidates(ticketId: string): Promise<NextHandlerCandidate[]> { return apiRequest<NextHandlerCandidate[]>(`/tickets/${encodeURIComponent(ticketId)}/workflow/assignment-candidates`) },
  async getProcessingDetails(ticketId: string): Promise<TicketProcessingDetails> { return apiRequest<TicketProcessingDetails>(`/tickets/${encodeURIComponent(ticketId)}/processing-details`) },
  async saveProcessingDetails(ticketId: string, version: number, details: TicketProcessingDetailsInput): Promise<TicketProcessingDetails> {
    return apiRequest<TicketProcessingDetails>(`/tickets/${encodeURIComponent(ticketId)}/processing-details`, {
      method: 'PUT', headers: { 'If-Match': `"${version}"` }, body: details,
    })
  },

  async preflightControlledJump(ticketId: string, requestId: string): Promise<ControlledJumpPreflight> {
    return apiRequest<ControlledJumpPreflight>(`/tickets/${encodeURIComponent(ticketId)}/workflow/approval-requests/${encodeURIComponent(requestId)}/preflight`)
  },

  async executeControlledJump(ticketId: string, requestId: string, version: number): Promise<Ticket> {
    return apiRequest<Ticket>(`/tickets/${encodeURIComponent(ticketId)}/workflow/approval-requests/${encodeURIComponent(requestId)}/execute`, {
      method: 'POST',
      headers: { 'If-Match': `"${version}"`, 'Idempotency-Key': newIdempotencyKey() },
    })
  },

  async listApprovalTasks(page = 1, pageSize = 20): Promise<ApprovalTaskInbox> {
    return apiRequest<ApprovalTaskInbox>(`/workflow/approval-tasks?page=${page}&pageSize=${pageSize}`)
  },

  async decideControlledJump(ticketId: string, requestId: string, decision: 'APPROVED' | 'REJECTED', reason: string): Promise<void> {
    await apiRequest(`/tickets/${encodeURIComponent(ticketId)}/workflow/approval-requests/${encodeURIComponent(requestId)}/decisions`, {
      method: 'POST', body: { decision, reason },
    })
  },

  async decideHandover(ticketId: string, requestId: string, decision: 'ACCEPTED' | 'REJECTED', reason: string): Promise<void> {
    await apiRequest(`/tickets/${encodeURIComponent(ticketId)}/workflow/handover-requests/${encodeURIComponent(requestId)}/decisions`, {
      method: 'POST', body: { decision, reason },
    })
  },

  async decideCoHandler(ticketId: string, requestId: string, decision: 'ACCEPTED' | 'REJECTED', reason: string): Promise<void> {
    await apiRequest(`/tickets/${encodeURIComponent(ticketId)}/workflow/cohandler-requests/${encodeURIComponent(requestId)}/decisions`, {
      method: 'POST', body: { decision, reason },
    })
  },

  async decideLifecycleActionApproval(ticketId: string, requestId: string, decision: 'APPROVED' | 'REJECTED', reason: string): Promise<void> {
    await apiRequest(`/tickets/${encodeURIComponent(ticketId)}/workflow/lifecycle-approval-requests/${encodeURIComponent(requestId)}/decisions`, {
      method: 'POST', body: { decision, reason },
    })
  },

  async listInternalComments(ticketId: string): Promise<TicketPageResult<TicketComment>> {
    // Internal comments belong to the workflow aggregate.  The list is deliberately read
    // through its authorized overview endpoint instead of exposing a second, unaudited
    // comments route.
    const overview = await ticketApi.getWorkflowOverview(ticketId)
    return {
      items: overview.comments.map((comment) => ({
        id: comment.id,
        visibility: 'INTERNAL' as const,
        author: {
          iamUserId: comment.authorIamUserId,
          // The backend intentionally does not disclose other users' profile details in this
          // workflow view. The IAM ID remains the auditable actor reference.
          displayName: comment.authorIamUserId,
          organizationName: '受控 IAM 投影',
          capturedAt: comment.createdAt,
        },
        content: comment.body,
        createdAt: comment.createdAt,
      })),
      page: 1,
      pageSize: 50,
      total: overview.comments.length,
    }
  },

  async listAttachments(ticketId: string): Promise<TicketAttachmentPage> {
    const attachments = await apiRequest<TicketAttachmentWire[]>(`/tickets/${encodeURIComponent(ticketId)}/attachments`)
    const scanState: Record<TicketAttachmentWire['scanStatus'], AttachmentScanState> = {
      QUARANTINED: 'QUARANTINED', CLEAN: 'SCAN_PASSED', REJECTED: 'REJECTED', SCAN_FAILED: 'SCAN_UNAVAILABLE',
    }
    return {
      items: attachments.map((attachment) => ({
        id: attachment.id,
        displayFileName: attachment.filename,
        detectedMediaType: attachment.detectedMediaType,
        sizeBytes: attachment.sizeBytes,
        scanState: scanState[attachment.scanStatus],
        downloadable: attachment.scanStatus === 'CLEAN',
      })),
      total: attachments.length,
    }
  },

  async getSla(ticketId: string): Promise<TicketSlaResponse> {
    return apiRequest<TicketSlaResponse>(`/tickets/${encodeURIComponent(ticketId)}/sla`)
  },

  async listRelations(ticketId: string): Promise<TicketRelation[]> {
    return apiRequest<TicketRelation[]>(`/tickets/${encodeURIComponent(ticketId)}/relations`)
  },

  async createRelation(ticketId: string, request: { targetTicketId: string; relationType: TicketRelationType }): Promise<TicketRelation> {
    return apiRequest<TicketRelation>(`/tickets/${encodeURIComponent(ticketId)}/relations`, { method: 'POST', body: request })
  },

  async createInternalComment(ticketId: string, request: { version: number; reason: string; content: string; structuredFields?: Record<string, string> }): Promise<void> {
    await ticketApi.executeAction(ticketId, { action: 'INTERNAL_COMMENT', version: request.version, reason: request.reason, comment: request.content })
  },
}

export interface TicketPageResult<T> { items: T[]; page: number; pageSize: number; total: number }
