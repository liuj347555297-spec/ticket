package cn.servicehub.workflow.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import cn.servicehub.notification.application.NotificationService;
import cn.servicehub.sla.application.SlaService;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.workflow.domain.CollaborationRole;
import cn.servicehub.workflow.domain.ControlledJumpRequest;
import cn.servicehub.workflow.domain.ApprovalPolicySnapshot;
import cn.servicehub.workflow.domain.ApprovalDecisionRecord;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import cn.servicehub.workflow.engine.WorkflowEngineInstance;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import cn.servicehub.workflow.engine.WorkflowApprovalTask;
import cn.servicehub.workflow.engine.WorkflowApprovalDecisionResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-controlled lifecycle and collaboration coordinator. It never accepts browser status, role or assignee data. */
@Service
public class TicketWorkflowService {
    private static final Set<String> SUPPORT = Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private static final Set<String> APPROVAL_CANDIDATE_ROLES = Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private static final String NO_POLICY_VERSION = "NONE";
    private final TicketWorkflowRepository workflowRepository;
    private final TicketRepository ticketRepository;
    private final WorkflowEnginePort workflowEngine;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectAuthorizationService authorizationService;
    private final IamUserProjectionRepository iamUsers;
    private final IamRoleProjectionRepository iamRoles;
    private final AuditEventPublisher audit;
    private final NotificationService notifications;
    private final SlaService slaService;
    private final ControlledJumpApprovalPolicyResolver approvalPolicyResolver;
    private final Clock clock = Clock.systemUTC();

    public TicketWorkflowService(TicketWorkflowRepository workflowRepository, TicketRepository ticketRepository,
                                 WorkflowEnginePort workflowEngine, CurrentUserProvider currentUserProvider,
                                 ObjectAuthorizationService authorizationService, IamUserProjectionRepository iamUsers,
                                 IamRoleProjectionRepository iamRoles,
                                 AuditEventPublisher audit, NotificationService notifications, SlaService slaService,
                                 ControlledJumpApprovalPolicyResolver approvalPolicyResolver) {
        this.workflowRepository = workflowRepository; this.ticketRepository = ticketRepository; this.workflowEngine = workflowEngine;
        this.currentUserProvider = currentUserProvider; this.authorizationService = authorizationService; this.iamUsers = iamUsers; this.iamRoles = iamRoles; this.audit = audit; this.notifications = notifications; this.slaService = slaService; this.approvalPolicyResolver = approvalPolicyResolver;
    }

    @Transactional
    public void startTicket(Ticket ticket, CurrentUser creator) {
        WorkflowEngineInstance engine = workflowEngine.start(ticket.id());
        Instant now = clock.instant();
        WorkflowInstance instance = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.SUBMITTED,
            null, 0, null, 0, now, now);
        workflowRepository.create(instance, task(ticket.id(), engine, "ROLE_FIRST_LINE_SUPPORT", null, null, now));
        record(creator, "WORKFLOW_STARTED", ticket.id(), Map.of("node", engine.nodeKey()));
    }

    @Transactional
    public Ticket act(String ticketId, WorkflowActionCommand command) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireTicketAction(actor, ticket, command.action());
        if (ticket.version() != command.expectedTicketVersion()) throw new WorkflowConflictException();
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        Instant now = clock.instant();
        Ticket result = switch (command.action()) {
            case CLAIM -> claim(ticket, instance, actor, now);
            case TRANSFER, HANDOVER -> transfer(ticket, instance, actor, command.targetIamUserId(), now);
            case ADD_COHANDLER -> coHandle(ticket, instance, actor, command.targetIamUserId(), now);
            case INTERNAL_COMMENT -> comment(ticket, actor, command.comment(), now);
            case CONTROLLED_JUMP_REQUEST -> requestJump(ticket, instance, actor, command.targetNode(), command.reason(), now);
            case HOLD -> hold(ticket, instance, actor, command.reason(), now);
            case RESUME -> resume(ticket, instance, actor, now);
            case ESCALATE -> escalate(ticket, instance, actor, command.reason(), now);
            case CANCEL -> cancel(ticket, instance, actor, command.reason(), now);
            case REOPEN -> reopen(ticket, instance, actor, now);
            default -> advanceLifecycle(ticket, instance, actor, command, now);
        };
        slaService.onTicketStateChanged(ticket, result);
        record(actor, "WORKFLOW_" + command.action().name(), ticketId, Map.of("from", ticket.status().name(), "to", result.status().name()));
        notifications.workflowAction(result, command.action().name(), actor.iamUserId(), notificationTarget(command));
        return result;
    }

    public WorkflowOverview overview(String ticketId) {
        // The controller first resolves the ticket through TicketService, including object-level read authorization.
        // Actions are still calculated again at write time; this list is never an authorization grant.
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        var approvalRequests = workflowRepository.findJumpRequests(ticketId);
        return new WorkflowOverview(instance, workflowRepository.findTasks(ticketId), workflowRepository.findComments(ticketId),
            availableActions(actor, ticket, instance), workflowRepository.findEvents(ticketId), workflowRepository.findActiveParticipants(ticketId),
            approvalRequests, approvalRequests.stream().flatMap(request -> workflowRepository.findApprovalDecisions(ticketId, request.id()).stream()).toList(),
            controlledJumpActions(actor, ticket, instance));
    }

    /**
     * Reads live Flowable candidate tasks and then joins them to authorized ticket projections.
     * The workflow request projection alone is never accepted as proof that an approval remains open.
     */
    public ApprovalTaskInbox approvalInbox(int page, int pageSize) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Set<String> candidateRoles = actor.authorities().stream().filter(APPROVAL_CANDIDATE_ROLES::contains)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (candidateRoles.isEmpty()) throw new AccessDeniedException("Approval inbox requires service-manager authority");
        int offset = Math.multiplyExact(page - 1, pageSize);
        List<ApprovalTaskInboxItem> items = workflowEngine.findPendingControlledJumpApprovalTasks(actor.iamUserId(), offset, pageSize).stream()
            .map(task -> toApprovalInboxItem(actor, task)).flatMap(java.util.Optional::stream).toList();
        audit.publish(new AuditEvent(clock.instant(), requestId(), actor.iamUserId(), "APPROVAL_TASK_INBOX_READ", "workflow-approval-task", "collection",
            Map.of("returned", String.valueOf(items.size()), "page", String.valueOf(page))));
        return new ApprovalTaskInbox(items, page, pageSize);
    }

    private java.util.Optional<ApprovalTaskInboxItem> toApprovalInboxItem(CurrentUser actor, WorkflowApprovalTask engineTask) {
        return workflowRepository.findJumpRequestById(engineTask.approvalRequestId())
            .filter(request -> "PENDING_APPROVAL".equals(request.status()))
            .flatMap(request -> ticketRepository.findById(request.ticketId()).flatMap(ticket -> {
                try {
                    authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.READ,
                        Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
                    boolean selfApproval = actor.iamUserId().equals(request.applicantIamUserId());
                    boolean policyCandidate = request.approvalPolicy() != null
                        && request.approvalPolicy().candidateIamUserIds().contains(actor.iamUserId());
                    boolean definitionRecorded = request.approvalPolicy() != null && request.approvalPolicy().hasRecordedDefinition();
                    String reason = selfApproval ? "申请人不得审批自己的申请" : !definitionRecorded ? "历史审批未记录流程定义，禁止继续审批" : !policyCandidate ? "当前人员不在该审批申请的冻结候选范围内" : null;
                    int candidateApprovalCount = request.approvalPolicy() == null ? 0 : request.approvalPolicy().candidateIamUserIds().size();
                    String decisionMode = request.approvalPolicy() == null ? "UNRECORDED" : request.approvalPolicy().decisionMode();
                    int requiredApprovalCount = "ALL_OF".equals(decisionMode) ? candidateApprovalCount : 1;
                    return java.util.Optional.of(new ApprovalTaskInboxItem(request, ticket, engineTask.createdAt(), decisionMode,
                        candidateApprovalCount, requiredApprovalCount, reason == null, reason));
                } catch (AccessDeniedException ignored) {
                    return java.util.Optional.empty();
                }
            }));
    }

    public ControlledJumpPreflight preflight(String ticketId, String requestId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireControlledJumpManager(actor, ticket);
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        ControlledJumpRequest request = workflowRepository.findJumpRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        return evaluatePreflight(ticket, instance, request);
    }

    private ControlledJumpPreflight evaluatePreflight(Ticket ticket, WorkflowInstance instance, ControlledJumpRequest request) {
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if (!"APPROVED".equals(request.status())) reasons.add("审批尚未通过");
        if (request.sourceTicketVersion() == null || request.sourceTicketVersion() != ticket.version()) reasons.add("工单版本已变化");
        if (request.sourceWorkflowVersion() == null || request.sourceWorkflowVersion() != instance.version()) reasons.add("流程版本已变化");
        if (!request.sourceNode().equals(instance.currentNode())) reasons.add("当前流程节点已变化");
        if (!Set.of("classify", "assign", "accept", "processing", "user_feedback", "closure").contains(request.targetNode())) reasons.add("目标节点不在发布白名单");
        if (request.targetNode().equals(instance.currentNode())) reasons.add("目标节点与当前节点相同");
        return new ControlledJumpPreflight(reasons.isEmpty(), List.copyOf(reasons),
            "CANCEL_CURRENT_TASK_WITH_AUDIT", candidateRoleForNode(request.targetNode()), candidateResolutionForNode(request.targetNode()), true,
            "RECALCULATE_REQUIRED_ON_EXECUTION", "RECOMPUTE_RECIPIENTS_ON_EXECUTION");
    }

    @Transactional
    public Ticket executeApprovedJump(String ticketId, String requestId, long expectedVersion) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        if (ticket.version() != expectedVersion) throw new WorkflowConflictException();
        requireControlledJumpManager(actor, ticket);
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        ControlledJumpRequest request = workflowRepository.findJumpRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        ControlledJumpPreflight plan = evaluatePreflight(ticket, instance, request);
        if (!plan.executable()) throw new WorkflowStateException();
        Instant now = clock.instant();
        if (!workflowRepository.claimJumpExecution(ticketId, requestId, ticket.version(), instance.version(), actor.iamUserId(), now)) throw new WorkflowConflictException();
        try {
            WorkflowEngineInstance moved = workflowEngine.moveControlledActivity(instance.engineInstanceId(), instance.currentNode(), request.targetNode());
            workflowRepository.findOpenTask(ticketId, instance.currentNode()).ifPresent(old -> workflowRepository.saveTask(new WorkflowTask(old.id(), old.ticketId(), old.engineTaskId(), old.nodeKey(), WorkflowTaskStatus.CANCELLED, old.candidateRole(), old.candidateIamUserId(), old.assigneeIamUserId(), old.collaborationRole(), old.version() + 1, old.createdAt(), now)));
            TicketStatus status = statusForNode(request.targetNode());
            if (!ticketRepository.updateStatus(ticketId, ticket.version(), status, now)) throw new WorkflowConflictException();
            WorkflowInstance changed = new WorkflowInstance(ticketId, instance.engineInstanceId(), moved.nodeKey(), status, null, instance.escalationLevel(), instance.primaryAssigneeIamUserId(), instance.version() + 1, instance.createdAt(), now);
            if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
            workflowRepository.saveTask(task(ticketId, moved, candidateRoleForNode(request.targetNode()), candidateUserForNode(ticket, request.targetNode()), null, now));
            if (!workflowRepository.completeJumpExecution(ticketId, requestId, instance.currentNode(), moved.nodeKey(), now)) throw new WorkflowConflictException();
            Ticket result = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
            slaService.onTicketStateChanged(ticket, result);
            notifications.workflowAction(result, "CONTROLLED_JUMP_EXECUTED", actor.iamUserId(), candidateUserForNode(ticket, request.targetNode()));
            record(actor, "CONTROLLED_JUMP_EXECUTED", ticketId, Map.of("approvalRequestId", requestId, "from", instance.currentNode(), "to", request.targetNode()));
            return result;
        } catch (RuntimeException exception) {
            workflowRepository.releaseJumpExecution(ticketId, requestId);
            record(actor, "CONTROLLED_JUMP_EXECUTION_FAILED", ticketId, Map.of("approvalRequestId", requestId, "reasonCode", "ENGINE_EXECUTION_FAILED"));
            if (exception instanceof WorkflowConflictException || exception instanceof WorkflowStateException) throw exception;
            throw new WorkflowExecutionUnavailableException();
        }
    }

    private String candidateRoleForNode(String node) {
        return switch (node) {
            case "assign" -> "ROLE_SERVICE_MANAGER";
            case "user_feedback", "closure" -> "ROLE_REQUESTER";
            default -> "ROLE_FIRST_LINE_SUPPORT";
        };
    }
    private String candidateResolutionForNode(String node) {
        return switch (node) {
            case "user_feedback", "closure" -> "REQUESTER_SNAPSHOT_REVALIDATION";
            default -> "IAM_ROLE_POOL_AND_DUTY_SCOPE_RECALCULATION";
        };
    }
    private String candidateUserForNode(Ticket ticket, String node) { return Set.of("user_feedback", "closure").contains(node) ? ticket.requester().iamUserId() : null; }
    private TicketStatus statusForNode(String node) { return switch (node) { case "classify" -> TicketStatus.SUBMITTED; case "assign" -> TicketStatus.PENDING_ASSIGNMENT; case "accept" -> TicketStatus.PENDING_ACCEPTANCE; case "processing" -> TicketStatus.IN_PROGRESS; case "user_feedback" -> TicketStatus.PENDING_USER_FEEDBACK; case "closure" -> TicketStatus.RESOLVED; default -> throw new WorkflowStateException(); }; }

    @Transactional
    public ControlledJumpRequest decideJumpRequest(String ticketId, String requestId, String decision, String reason) {
        if (!Set.of("APPROVED", "REJECTED").contains(decision) || validText(reason, 1000) == null) throw new IllegalArgumentException("Approval decision is invalid");
        CurrentUser approver = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireControlledJumpManager(approver, ticket);
        ControlledJumpRequest request = workflowRepository.findJumpRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if (approver.iamUserId().equals(request.applicantIamUserId())) throw new AccessDeniedException("Applicant cannot approve own request");
        if (request.approvalPolicy() == null || !request.approvalPolicy().hasRecordedDefinition()) throw new WorkflowStateException();
        if (approver.authorities().stream().noneMatch(request.approvalPolicy().candidateRoles()::contains)) throw new AccessDeniedException("Approver is outside the frozen candidate roles");
        Instant now = clock.instant();
        if (request.approvalEngineInstanceId() == null) throw new WorkflowStateException();
        if (!request.approvalPolicy().candidateIamUserIds().contains(approver.iamUserId())) throw new AccessDeniedException("Approver is outside the frozen candidate users");
        WorkflowApprovalDecisionResult outcome = workflowEngine.decideControlledJumpApproval(request.approvalEngineInstanceId(), approver.iamUserId(), decision);
        workflowRepository.appendApprovalDecision(new ApprovalDecisionRecord(UUID.randomUUID().toString(), requestId, outcome.engineTaskId(),
            approver.iamUserId(), decision, reason.trim(), now));
        if (outcome.processCompleted() && !workflowRepository.finalizeJumpRequestApproval(ticketId, requestId, outcome.finalDecision(), approver.iamUserId(), reason.trim(), now)) {
            throw new WorkflowConflictException();
        }
        record(approver, "CONTROLLED_JUMP_" + decision, ticketId, Map.of("approvalRequestId", requestId, "targetNode", request.targetNode(),
            "processCompleted", String.valueOf(outcome.processCompleted())));
        return workflowRepository.findJumpRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
    }

    private java.util.List<WorkflowAvailableAction> availableActions(CurrentUser actor, Ticket ticket, WorkflowInstance instance) {
        boolean support = hasSupport(actor);
        boolean requester = actor.iamUserId().equals(ticket.requester().iamUserId());
        boolean primary = actor.iamUserId().equals(instance.primaryAssigneeIamUserId());
        boolean manager = actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN");
        boolean handler = primary || workflowRepository.hasCoHandler(ticket.id(), actor.iamUserId());
        java.util.List<WorkflowAvailableAction> result = new java.util.ArrayList<>();
        switch (ticket.status()) {
            case SUBMITTED, PENDING_CLASSIFICATION -> { if (support) add(result, WorkflowAction.CLASSIFY, "分类", false); }
            case PENDING_ASSIGNMENT -> { if (support) add(result, WorkflowAction.ASSIGN, "分派", true); }
            case PENDING_ACCEPTANCE -> {
                if (support && instance.primaryAssigneeIamUserId() == null) add(result, WorkflowAction.CLAIM, "抢单", false);
                if (primary) add(result, WorkflowAction.ACCEPT, "受理", false);
            }
            case IN_PROGRESS -> {
                if (handler) add(result, WorkflowAction.REQUEST_USER_FEEDBACK, "待用户反馈", false);
                if (primary || manager) {
                    add(result, WorkflowAction.TRANSFER, "转办", true);
                    add(result, WorkflowAction.ADD_COHANDLER, "添加协办", true);
                    add(result, WorkflowAction.HANDOVER, "交接班", true);
                    add(result, WorkflowAction.HOLD, "挂起", false);
                }
                if (support) add(result, WorkflowAction.ESCALATE, "升级", false);
            }
            case PENDING_USER_FEEDBACK -> { if (handler) add(result, WorkflowAction.RESOLVE, "解决", false); }
            case RESOLVED -> { if (requester || handler) add(result, WorkflowAction.CLOSE, "关闭", false); }
            case ON_HOLD -> { if (primary || manager) add(result, WorkflowAction.RESUME, "恢复", false); }
            case CLOSED -> { if (requester || support) add(result, WorkflowAction.REOPEN, "重开", false); }
            default -> { }
        }
        if ((support || requester) && ticket.status() != TicketStatus.CLOSED && ticket.status() != TicketStatus.CANCELLED) add(result, WorkflowAction.CANCEL, "撤销", false);
        if (support && ticket.status() != TicketStatus.CLOSED && ticket.status() != TicketStatus.CANCELLED) {
            add(result, WorkflowAction.INTERNAL_COMMENT, "内部评论", false);
        }
        return java.util.List.copyOf(result);
    }

    private java.util.List<ControlledJumpAvailableAction> controlledJumpActions(CurrentUser actor, Ticket ticket, WorkflowInstance instance) {
        if (!isControlledJumpManager(actor)) return List.of();
        return workflowRepository.findJumpRequests(ticket.id()).stream()
            .filter(request -> "APPROVED".equals(request.status()))
            .map(request -> {
                ControlledJumpPreflight preflight = evaluatePreflight(ticket, instance, request);
                String disabledReason = preflight.executable() ? null : String.join("；", preflight.blockingReasons());
                return new ControlledJumpAvailableAction(request.id(), true, preflight.executable(), disabledReason);
            })
            .toList();
    }

    private void add(java.util.List<WorkflowAvailableAction> actions, WorkflowAction action, String label, boolean requiresTarget) {
        actions.add(new WorkflowAvailableAction(action.name(), label, requiresTarget));
    }

    private Ticket advanceLifecycle(Ticket ticket, WorkflowInstance instance, CurrentUser actor, WorkflowActionCommand command, Instant now) {
        WorkflowAction action = command.action();
        Transition transition = Transition.forAction(ticket.status(), action);
        assertHandler(actor, ticket, instance, action);
        String assignedTarget = action == WorkflowAction.ASSIGN ? requireActiveSupportTargetAndReturn(command.targetIamUserId()) : instance.primaryAssigneeIamUserId();
        WorkflowEngineInstance engine = workflowEngine.advance(instance.engineInstanceId(), transition.expectedNode());
        finishOpenTask(ticket.id(), transition.expectedNode(), actor.iamUserId(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), transition.nextStatus(), now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), transition.nextStatus(), null,
            instance.escalationLevel(), assignedTarget, instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        if (action == WorkflowAction.ASSIGN) workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(assignedTarget, now), now);
        if (engine.taskId() != null) workflowRepository.saveTask(task(ticket.id(), engine, transition.nextCandidateRole(), transition.nextCandidateUser(ticket, assignedTarget), null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket claim(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        assertSupport(actor); if (instance.primaryAssigneeIamUserId() != null) throw new WorkflowStateException();
        WorkflowTask open = workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).orElseThrow(WorkflowStateException::new);
        WorkflowTask claimed = new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED,
            open.candidateRole(), actor.iamUserId(), actor.iamUserId(), CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), now);
        workflowRepository.saveTask(claimed); updateInstanceAssignee(instance, actor.iamUserId(), now); workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(actor.iamUserId(), now), now); return ticket;
    }

    private Ticket transfer(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, Instant now) {
        assertPrimaryOrManager(actor, instance); requireActiveSupportTarget(target); updateInstanceAssignee(instance, target, now); workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(target, now), now);
        workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).ifPresent(open -> workflowRepository.saveTask(new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED, open.candidateRole(), target, target, CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), now)));
        return ticket;
    }

    private Ticket coHandle(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, Instant now) {
        assertPrimaryOrManager(actor, instance); requireActiveSupportTarget(target); workflowRepository.addCoHandler(ticket.id(), target, now); workflowRepository.addCoHandlerParticipant(ticket.id(), snapshot(target, now), now); return ticket;
    }

    private Ticket comment(Ticket ticket, CurrentUser actor, String comment, Instant now) {
        if (comment == null || comment.isBlank() || comment.length() > 2000) throw new IllegalArgumentException("Comment is invalid");
        workflowRepository.addComment(new WorkflowComment(UUID.randomUUID().toString(), ticket.id(), actor.iamUserId(), comment.trim(), now)); return ticket;
    }

    private Ticket requestJump(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String targetNode, String reason, Instant now) {
        assertSupport(actor); if (!Set.of("classify", "assign", "accept", "processing", "user_feedback", "closure").contains(targetNode) || validText(reason, 1000) == null) throw new IllegalArgumentException("Jump request is invalid");
        String requestId = UUID.randomUUID().toString();
        var definition = workflowEngine.resolveControlledJumpApprovalDefinition();
        // Never create a self-approval task. In ALL_OF mode including the applicant would make
        // an otherwise valid request impossible to finish, while in ANY_ONE it would expose an
        // unnecessary forbidden task. The immutable snapshot records the post-exclusion set.
        Set<String> candidateUsers = iamRoles.findActiveIamUserIdsByRoleCodes(APPROVAL_CANDIDATE_ROLES).stream()
            .filter(candidate -> !actor.iamUserId().equals(candidate)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (candidateUsers.isEmpty()) throw new WorkflowStateException();
        String decisionMode = approvalPolicyResolver.decisionModeFor(targetNode);
        ApprovalPolicySnapshot policy = new ApprovalPolicySnapshot(definition.processKey(), definition.processDefinitionId(), definition.version(),
            APPROVAL_CANDIDATE_ROLES, decisionMode, NO_POLICY_VERSION, NO_POLICY_VERSION, now, candidateUsers);
        var approval = workflowEngine.startControlledJumpApproval(requestId, ticket.id(), actor.iamUserId(), definition, candidateUsers, decisionMode);
        workflowRepository.addJumpRequest(new ControlledJumpRequest(requestId, ticket.id(), actor.iamUserId(), instance.currentNode(), targetNode,
            reason.trim(), "PENDING_APPROVAL", now, null, null, null, approval.instanceId(), ticket.version(), instance.version(),
            null, null, null, null, null, null, policy)); return ticket;
    }

    private Ticket hold(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String reason, Instant now) {
        assertPrimaryOrManager(actor, instance); if (ticket.status() == TicketStatus.ON_HOLD || validText(reason, 1000) == null) throw new WorkflowStateException();
        return updateStatusAndInstance(ticket, instance, TicketStatus.ON_HOLD, ticket.status(), instance.escalationLevel(), now);
    }
    private Ticket resume(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        assertPrimaryOrManager(actor, instance); if (ticket.status() != TicketStatus.ON_HOLD || instance.resumeStatus() == null) throw new WorkflowStateException();
        return updateStatusAndInstance(ticket, instance, instance.resumeStatus(), null, instance.escalationLevel(), now);
    }
    private Ticket escalate(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String reason, Instant now) {
        assertSupport(actor); if (validText(reason, 1000) == null) throw new IllegalArgumentException("Escalation reason is required");
        return updateStatusAndInstance(ticket, instance, ticket.status(), instance.resumeStatus(), instance.escalationLevel() + 1, now);
    }
    private Ticket cancel(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String reason, Instant now) {
        if (!(actor.iamUserId().equals(ticket.requester().iamUserId()) || hasSupport(actor))) throw new AccessDeniedException("Cancellation is not authorized");
        if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED || validText(reason, 1000) == null) throw new WorkflowStateException();
        workflowEngine.cancel(instance.engineInstanceId(), "ticket-cancelled"); return updateStatusAndInstance(ticket, instance, TicketStatus.CANCELLED, null, instance.escalationLevel(), now);
    }
    private Ticket reopen(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        if (!(actor.iamUserId().equals(ticket.requester().iamUserId()) || hasSupport(actor)) || ticket.status() != TicketStatus.CLOSED) throw new WorkflowStateException();
        WorkflowEngineInstance engine = workflowEngine.start(ticket.id());
        workflowRepository.clearPrimaryParticipant(ticket.id(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), TicketStatus.PENDING_CLASSIFICATION, now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.PENDING_CLASSIFICATION, null, instance.escalationLevel(), null, instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        workflowRepository.saveTask(task(ticket.id(), engine, "ROLE_FIRST_LINE_SUPPORT", null, null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket updateStatusAndInstance(Ticket ticket, WorkflowInstance instance, TicketStatus status, TicketStatus resumeStatus, int escalationLevel, Instant now) {
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), status, now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), instance.engineInstanceId(), instance.currentNode(), status, resumeStatus,
            escalationLevel, instance.primaryAssigneeIamUserId(), instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private void finishOpenTask(String ticketId, String node, String actor, Instant now) { workflowRepository.findOpenTask(ticketId, node).ifPresent(task -> workflowRepository.saveTask(new WorkflowTask(task.id(), task.ticketId(), task.engineTaskId(), task.nodeKey(), WorkflowTaskStatus.COMPLETED, task.candidateRole(), task.candidateIamUserId(), actor, task.collaborationRole(), task.version() + 1, task.createdAt(), now))); }
    private void updateInstanceAssignee(WorkflowInstance instance, String assignee, Instant now) { if (!workflowRepository.updateInstance(new WorkflowInstance(instance.ticketId(), instance.engineInstanceId(), instance.currentNode(), instance.status(), instance.resumeStatus(), instance.escalationLevel(), assignee, instance.version() + 1, instance.createdAt(), now), instance.version())) throw new WorkflowConflictException(); }
    private WorkflowTask task(String ticketId, WorkflowEngineInstance engine, String role, String user, CollaborationRole collaborationRole, Instant now) { return new WorkflowTask(UUID.randomUUID().toString(), ticketId, engine.taskId(), engine.nodeKey(), WorkflowTaskStatus.OPEN, role, user, null, collaborationRole, 0, now, now); }
    private void requireTicketAction(CurrentUser actor, Ticket ticket, WorkflowAction action) { ObjectAction objectAction = action == WorkflowAction.INTERNAL_COMMENT ? ObjectAction.COMMENT : (action == WorkflowAction.TRANSFER || action == WorkflowAction.HANDOVER ? ObjectAction.TRANSFER : action == WorkflowAction.ASSIGN ? ObjectAction.ASSIGN : ObjectAction.UPDATE); authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), objectAction, Map.of("requesterIamUserId", ticket.requester().iamUserId()))); }
    private void requireControlledJumpManager(CurrentUser actor, Ticket ticket) {
        if (!isControlledJumpManager(actor)) throw new AccessDeniedException("Controlled jump management requires service-manager authority");
        authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.APPROVE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
    }
    private boolean isControlledJumpManager(CurrentUser actor) { return actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN"); }
    private void assertHandler(CurrentUser actor, Ticket ticket, WorkflowInstance instance, WorkflowAction action) { if (action == WorkflowAction.CLOSE && actor.iamUserId().equals(ticket.requester().iamUserId())) return; if (action == WorkflowAction.CLASSIFY || action == WorkflowAction.ASSIGN) { assertSupport(actor); return; } if (action == WorkflowAction.ACCEPT && actor.iamUserId().equals(instance.primaryAssigneeIamUserId())) return; if (instance.primaryAssigneeIamUserId() != null && (actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || workflowRepository.hasCoHandler(ticket.id(), actor.iamUserId()))) return; throw new AccessDeniedException("Only a server-resolved handler may process this ticket"); }
    private void assertPrimaryOrManager(CurrentUser actor, WorkflowInstance instance) { if (hasSupport(actor) && (actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN"))) return; throw new AccessDeniedException("Only primary handler or manager may perform this action"); }
    private void assertSupport(CurrentUser actor) { if (!hasSupport(actor)) throw new AccessDeniedException("Support role is required"); }
    private boolean hasSupport(CurrentUser actor) { return actor.authorities().stream().anyMatch(SUPPORT::contains); }
    private void requireActiveTarget(String target) { requireActiveTargetAndReturn(target); }
    private String requireActiveTargetAndReturn(String target) { if (target == null || iamUsers.findActiveByIamUserId(target).isEmpty()) throw new IllegalArgumentException("Target IAM user is unavailable"); return target; }
    private void requireActiveSupportTarget(String target) { requireActiveSupportTargetAndReturn(target); }
    private String requireActiveSupportTargetAndReturn(String target) {
        requireActiveTargetAndReturn(target);
        if (!iamRoles.hasAnyActiveRole(target, SUPPORT)) throw new IllegalArgumentException("Target IAM user is not an active support handler");
        return target;
    }
    private cn.servicehub.ticket.domain.IdentitySnapshot snapshot(String iamUserId, Instant now) {
        var user = iamUsers.findActiveByIamUserId(iamUserId).orElseThrow(() -> new IllegalArgumentException("Target IAM user is unavailable"));
        var position = user.positions().stream().filter(cn.servicehub.iam.domain.PositionSummary::primary).findFirst().or(() -> user.positions().stream().findFirst()).map(cn.servicehub.iam.domain.PositionSummary::name).orElse(null);
        return new cn.servicehub.ticket.domain.IdentitySnapshot(user.iamUserId(), user.displayName(), user.organization().iamOrganizationId(), user.organization().name(), position, now);
    }
    private String validText(String text, int max) { return text == null || text.isBlank() || text.length() > max ? null : text; }
    private String notificationTarget(WorkflowActionCommand command) {
        return switch (command.action()) {
            case ASSIGN, TRANSFER, HANDOVER, ADD_COHANDLER -> command.targetIamUserId();
            default -> null;
        };
    }
    private void record(CurrentUser actor, String action, String ticketId, Map<String, String> detail) { String requestId = MDC.get("requestId") == null ? "system" : MDC.get("requestId"); Instant now = clock.instant(); workflowRepository.appendEvent(ticketId, action, actor.iamUserId(), requestId, detail, now); audit.publish(new AuditEvent(now, requestId, actor.iamUserId(), action, "ticket", ticketId, detail)); }
    private String requestId() { return MDC.get("requestId") == null ? "system" : MDC.get("requestId"); }

    private record Transition(String expectedNode, TicketStatus nextStatus, String nextCandidateRole) {
        static Transition forAction(TicketStatus status, WorkflowAction action) {
            if (action == WorkflowAction.CLASSIFY && (status == TicketStatus.SUBMITTED || status == TicketStatus.PENDING_CLASSIFICATION)) return new Transition("classify", TicketStatus.PENDING_ASSIGNMENT, "ROLE_SERVICE_MANAGER");
            if (action == WorkflowAction.ASSIGN && status == TicketStatus.PENDING_ASSIGNMENT) return new Transition("assign", TicketStatus.PENDING_ACCEPTANCE, "ROLE_FIRST_LINE_SUPPORT");
            if (action == WorkflowAction.ACCEPT && status == TicketStatus.PENDING_ACCEPTANCE) return new Transition("accept", TicketStatus.IN_PROGRESS, "ROLE_FIRST_LINE_SUPPORT");
            if (action == WorkflowAction.REQUEST_USER_FEEDBACK && status == TicketStatus.IN_PROGRESS) return new Transition("processing", TicketStatus.PENDING_USER_FEEDBACK, "ROLE_REQUESTER");
            if (action == WorkflowAction.RESOLVE && status == TicketStatus.PENDING_USER_FEEDBACK) return new Transition("user_feedback", TicketStatus.RESOLVED, "ROLE_REQUESTER");
            if (action == WorkflowAction.CLOSE && status == TicketStatus.RESOLVED) return new Transition("closure", TicketStatus.CLOSED, null);
            throw new WorkflowStateException();
        }
        String nextCandidateUser(Ticket ticket, String primaryAssignee) { return "ROLE_REQUESTER".equals(nextCandidateRole) ? ticket.requester().iamUserId() : primaryAssignee; }
    }
}
