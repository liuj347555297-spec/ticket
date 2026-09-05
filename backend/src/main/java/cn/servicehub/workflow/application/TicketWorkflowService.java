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
import cn.servicehub.workflow.domain.HandoverRequest;
import cn.servicehub.workflow.domain.CoHandlerRequest;
import cn.servicehub.workflow.domain.TicketDelegation;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRequest;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRepository;
import cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalEnginePort;
import cn.servicehub.workflow.engine.WorkflowEngineInstance;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import cn.servicehub.workflow.engine.WorkflowApprovalTask;
import cn.servicehub.workflow.engine.WorkflowInboxTask;
import cn.servicehub.workflow.engine.WorkflowApprovalDecisionResult;
import cn.servicehub.workflow.routing.NodeAssignmentResolver;
import cn.servicehub.workflow.routing.NodeAssignmentSnapshot;
import cn.servicehub.workflow.team.SupportQueueEligibilityService;
import cn.servicehub.workflow.team.SupportQueueRepository;
import cn.servicehub.workflow.team.WorkflowQueueRoutingSnapshot;
import cn.servicehub.ticket.application.TicketObjectContextResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Value;
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
    private final LifecycleActionApprovalRepository lifecycleApprovalRepository;
    private final LifecycleActionApprovalEnginePort lifecycleApprovalEngine;
    private final LifecycleApprovalPolicyResolver lifecycleApprovalPolicyResolver;
    private final ApprovalCandidateScopeResolver approvalCandidates;
    private final NodeAssignmentResolver nodeAssignments;
    private final SupportQueueEligibilityService queueEligibility;
    private final SupportQueueRepository supportQueues;
    private final TicketObjectContextResolver ticketContexts;
    private final boolean directAcceptRouting;
    private final Clock clock = Clock.systemUTC();

    public TicketWorkflowService(TicketWorkflowRepository workflowRepository, TicketRepository ticketRepository,
                                 WorkflowEnginePort workflowEngine, CurrentUserProvider currentUserProvider,
                                 ObjectAuthorizationService authorizationService, IamUserProjectionRepository iamUsers,
                                 IamRoleProjectionRepository iamRoles,
                                 AuditEventPublisher audit, NotificationService notifications, SlaService slaService,
                                 ControlledJumpApprovalPolicyResolver approvalPolicyResolver,
                                 LifecycleActionApprovalRepository lifecycleApprovalRepository,
                                 LifecycleActionApprovalEnginePort lifecycleApprovalEngine,
                                 LifecycleApprovalPolicyResolver lifecycleApprovalPolicyResolver,
                                 ApprovalCandidateScopeResolver approvalCandidates,
                                 NodeAssignmentResolver nodeAssignments,SupportQueueEligibilityService queueEligibility,
                                 SupportQueueRepository supportQueues,TicketObjectContextResolver ticketContexts,
                                 @Value("${servicehub.workflow.direct-accept-routing:false}") boolean directAcceptRouting) {
        this.workflowRepository = workflowRepository; this.ticketRepository = ticketRepository; this.workflowEngine = workflowEngine;
        this.currentUserProvider = currentUserProvider; this.authorizationService = authorizationService; this.iamUsers = iamUsers; this.iamRoles = iamRoles; this.audit = audit; this.notifications = notifications; this.slaService = slaService; this.approvalPolicyResolver = approvalPolicyResolver;
        this.lifecycleApprovalRepository = lifecycleApprovalRepository; this.lifecycleApprovalEngine = lifecycleApprovalEngine; this.lifecycleApprovalPolicyResolver = lifecycleApprovalPolicyResolver;
        this.approvalCandidates = approvalCandidates;
        this.nodeAssignments = nodeAssignments;
        this.queueEligibility=queueEligibility;this.supportQueues=supportQueues;this.ticketContexts=ticketContexts;
        this.directAcceptRouting = directAcceptRouting;
    }

    @Transactional
    public Ticket startTicket(Ticket ticket, CurrentUser creator) {
        if (!directAcceptRouting) {
            WorkflowEngineInstance engine = workflowEngine.start(ticket.id());
            Instant now = clock.instant();
            WorkflowInstance instance = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.SUBMITTED,
                null, 0, null, engine.processDefinitionId(), engine.processDefinitionVersion(), 0, now, now);
            workflowRepository.create(instance, task(ticket.id(), engine, "ROLE_FIRST_LINE_SUPPORT", null, null, now));
            record(creator, "WORKFLOW_STARTED", ticket.id(), Map.of("node", engine.nodeKey(), "routingMode", "LEGACY_CLASSIFY_ASSIGN"));
            return ticket;
        }
        // The requester already selected the service catalog/system in the structured form. New
        // incident work therefore enters accept directly; classification/assignment remain only
        // for historical instances and controlled migrations.
        WorkflowEngineInstance engine = workflowEngine.start(ticket.id());
        Instant now = clock.instant();
        var routing = nodeAssignments.resolveInitialAcceptance(ticket.id(), ticket.serviceCatalogItem().id(), ticket.requester().iamUserId());
        String assignee = routing.selectedIamUserId();
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), TicketStatus.PENDING_ACCEPTANCE, now)) throw new WorkflowConflictException();
        WorkflowInstance instance = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.PENDING_ACCEPTANCE,
            null, 0, assignee, engine.processDefinitionId(), engine.processDefinitionVersion(), 0, now, now);
        WorkflowTask initial=task(ticket.id(), engine, routing.policy().candidateRoles().iterator().next(), assignee, null, now,routing.policy().queueCode());
        workflowRepository.create(instance, initial);captureQueueSnapshot(initial);
        if (assignee != null) workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(assignee, now), now);
        record(creator, "WORKFLOW_STARTED", ticket.id(), Map.of("node", engine.nodeKey(), "routingMode", routing.policy().mode().name(),
            "assigneeResolved", String.valueOf(assignee != null), "noEligibleCandidate", String.valueOf(routing.noEligibleCandidate())));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    @Transactional
    public Ticket act(String ticketId, WorkflowActionCommand command) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireTicketAction(actor, ticket, command.action());
        if (ticket.version() != command.expectedTicketVersion()) throw new WorkflowConflictException();
        WorkflowInstance instance = (command.action() == WorkflowAction.TRANSFER
            ? workflowRepository.findInstanceForUpdate(ticketId)
            : workflowRepository.findInstance(ticketId)).orElseThrow(WorkflowStateException::new);
        if (hasActiveLifecycleApproval(ticket, instance, command.action())) throw new WorkflowConflictException();
        Instant now = clock.instant();
        boolean approvalRequested = requiresLifecycleApproval(ticket, command.action());
        Ticket result = switch (command.action()) {
            case CLAIM -> claim(ticket, instance, actor, now);
            case TRANSFER -> transfer(ticket, instance, actor, command.targetIamUserId(), now);
            case HANDOVER -> requestHandover(ticket, instance, actor, command.targetIamUserId(), command.reason(), now);
            case ADD_COHANDLER -> requestCoHandler(ticket, instance, actor, command.targetIamUserId(), command.reason(), now);
            case START_PROCESSING -> returnToProcessing(ticket, instance, actor, now);
            case INTERNAL_COMMENT -> comment(ticket, actor, command.comment(), now);
            case CONTROLLED_JUMP_REQUEST -> requestJump(ticket, instance, actor, command.targetNode(), command.reason(), now);
            // These actions are intentionally requests, never direct state/assignee changes.
            // The target for ASSIGN is frozen by the server before the dedicated Flowable task starts.
            case ACCEPT, RESOLVE, CLOSE -> approvalRequested
                ? requestLifecycleActionApproval(ticket, instance, actor, command.action(), command.targetIamUserId(), command.reason(), now)
                : advanceLifecycle(ticket, instance, actor, command, now);
            case HOLD, ESCALATE, CANCEL, REOPEN, ASSIGN ->
                requestLifecycleActionApproval(ticket, instance, actor, command.action(), command.targetIamUserId(), command.reason(), now);
            case RESUME -> resume(ticket, instance, actor, now);
            default -> advanceLifecycle(ticket, instance, actor, command, now);
        };
        // Approval submission has no state transition and therefore must not recalculate or
        // persist a misleading SLA state. The approved execution path performs this exactly once.
        if (!approvalRequested) slaService.onTicketStateChanged(ticket, result);
        record(actor, approvalRequested ? "LIFECYCLE_APPROVAL_REQUESTED" : "WORKFLOW_" + command.action().name(), ticketId,
            Map.of("from", ticket.status().name(), "to", result.status().name(), "action", command.action().name()));
        // A pending lifecycle request must not masquerade as a completed assignment or state
        // change. Its eventual committed notification is emitted after Flowable approval below.
        if (!approvalRequested) {
            notifications.workflowAction(result, command.action().name(), actor.iamUserId(), notificationTarget(command));
        }
        return result;
    }

    /** Controller performs ticket object authorization before revealing the exact instance definition. */
    public cn.servicehub.workflow.engine.WorkflowBpmnDiagram diagram(String ticketId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        var instance = workflowRepository.findInstance(ticketId).orElse(null);
        var diagram = instance == null ? cn.servicehub.workflow.engine.WorkflowBpmnDiagram.legacy()
            : workflowEngine.instanceDiagram(instance.processDefinitionId(), instance.processDefinitionVersion(), instance.engineInstanceId());
        record(actor, "WORKFLOW_DIAGRAM_READ", ticketId, Map.of("availability", diagram.availability()));
        return diagram;
    }

    public WorkflowOverview overview(String ticketId) {
        // The controller first resolves the ticket through TicketService, including object-level read authorization.
        // Actions are still calculated again at write time; this list is never an authorization grant.
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        List<WorkflowTask> tasks = workflowRepository.findTasks(ticketId);
        AcceptanceQueueReadModel acceptanceQueue = acceptanceQueue(instance, tasks);
        var approvalRequests = workflowRepository.findJumpRequests(ticketId);
        return new WorkflowOverview(instance, tasks, workflowRepository.findComments(ticketId),
            availableActions(actor, ticket, instance), workflowRepository.findEvents(ticketId), workflowRepository.findActiveParticipants(ticketId),
            approvalRequests, approvalRequests.stream().flatMap(request -> workflowRepository.findApprovalDecisions(ticketId, request.id()).stream()).toList(),
            controlledJumpActions(actor, ticket, instance), workflowRepository.findHandoverRequests(ticketId), workflowRepository.findCoHandlerRequests(ticketId),
            lifecycleApprovalRepository.findByTicketId(ticketId).stream().map(LifecycleActionApprovalSummary::from).toList(), nodeAssignments.snapshots(ticketId),
            acceptanceQueue.candidates(), acceptanceQueue.candidates().size());
    }

    /**
     * A routing snapshot remains append-only evidence, but it is only exposed as a current
     * candidate list while its exact task is open and the workflow still has no primary handler.
     * Missing or disabled IAM projections are omitted instead of leaking stale directory data.
     */
    private AcceptanceQueueReadModel acceptanceQueue(WorkflowInstance instance, List<WorkflowTask> tasks) {
        if (instance.primaryAssigneeIamUserId() != null) return AcceptanceQueueReadModel.empty();
        WorkflowTask active = tasks.stream()
            .filter(task -> task.nodeKey().equals(instance.currentNode()))
            .filter(task -> task.status() == WorkflowTaskStatus.OPEN)
            .filter(task -> task.assigneeIamUserId() == null && task.queueCode() != null)
            .max(Comparator.comparing(WorkflowTask::updatedAt).thenComparing(WorkflowTask::id))
            .orElse(null);
        if (active == null) return AcceptanceQueueReadModel.empty();
        WorkflowQueueRoutingSnapshot snapshot = supportQueues.findRoutingSnapshots(instance.ticketId()).stream()
            .filter(item -> item.workflowTaskId().equals(active.id()))
            .filter(item -> "SHARED_QUEUE".equals(item.assignment().mode()))
            .max(Comparator.comparing(WorkflowQueueRoutingSnapshot::capturedAt).thenComparing(WorkflowQueueRoutingSnapshot::id))
            .orElse(null);
        if (snapshot == null || !active.queueCode().equals(snapshot.queueCode())) return AcceptanceQueueReadModel.empty();
        List<AcceptanceCandidate> candidates = snapshot.candidateIamUserIds().stream()
            .flatMap(id -> iamUsers.findActiveByIamUserId(id)
                .filter(user -> user.active() && id.equals(user.iamUserId())).stream())
            .filter(user -> user.displayName() != null && !user.displayName().isBlank())
            .map(user -> new AcceptanceCandidate(user.iamUserId(), user.displayName(),
                user.organization() == null ? null : user.organization().name(),
                user.positions().stream().filter(cn.servicehub.iam.domain.PositionSummary::primary).findFirst()
                    .or(() -> user.positions().stream().findFirst()).map(cn.servicehub.iam.domain.PositionSummary::name).orElse(null)))
            .sorted(Comparator.comparing(AcceptanceCandidate::displayName).thenComparing(AcceptanceCandidate::iamUserId))
            .toList();
        return candidates.isEmpty() ? AcceptanceQueueReadModel.empty() : new AcceptanceQueueReadModel(candidates);
    }

    private record AcceptanceQueueReadModel(List<AcceptanceCandidate> candidates) {
        private static AcceptanceQueueReadModel empty() { return new AcceptanceQueueReadModel(List.of()); }
    }
    public List<NodeAssignmentResolver.HandlerCandidate> nextHandlerCandidates(String ticketId, String targetNode) {
        CurrentUser actor = currentUserProvider.requireCurrentUser(); Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        if (!directAcceptRouting || !"processing".equals(targetNode) || ticket.status() != TicketStatus.PENDING_ACCEPTANCE || !actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || !nodeAssignments.requiresPreviousHandlerSelection(ticket.serviceCatalogItem().id(), targetNode)) throw new AccessDeniedException("Next-handler candidates are not authorized");
        return nodeAssignments.candidates(ticket.id(), ticket.serviceCatalogItem().id(), targetNode, ticket.requester().iamUserId());
    }
    public List<NodeAssignmentResolver.HandlerCandidate> transferCandidates(String ticketId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        assertPrimaryOrManager(actor, instance);
        if (ticket.status() != TicketStatus.IN_PROGRESS || !"processing".equals(instance.currentNode())) throw new WorkflowStateException();
        return nodeAssignments.candidates(ticket.id(), ticket.serviceCatalogItem().id(), instance.currentNode(), ticket.requester().iamUserId()).stream()
            .filter(candidate -> !candidate.iamUserId().equals(instance.primaryAssigneeIamUserId()))
            .toList();
    }
    public List<NodeAssignmentResolver.HandlerCandidate> assignmentCandidates(String ticketId) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        assertSupport(actor);
        if (ticket.status() != TicketStatus.PENDING_ASSIGNMENT) throw new WorkflowStateException();
        return nodeAssignments.candidates(ticket.id(), ticket.serviceCatalogItem().id(), "accept", ticket.requester().iamUserId());
    }

    /**
     * The recipient's Flowable task, not a browser parameter, is the authority for accepting a
     * handover. A completed task may still become STALE when the source workflow changed.
     */
    @Transactional
    public HandoverRequest decideHandover(String ticketId, String requestId, String decision, String reason) {
        if (!Set.of("ACCEPTED", "REJECTED").contains(decision) || validText(reason, 1000) == null) throw new IllegalArgumentException("Handover decision is invalid");
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.UPDATE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
        HandoverRequest request = workflowRepository.findHandoverRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if (!"PENDING_CONFIRMATION".equals(request.status()) || !actor.iamUserId().equals(request.targetIamUserId())) throw new AccessDeniedException("Handover decision is not authorized");
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        workflowEngine.decideHandoverConfirmation(request.engineInstanceId(), actor.iamUserId(), decision);
        if (!workflowRepository.finalizeHandoverRequest(ticketId, requestId, decision, reason.trim(), clock.instant(), ticket.version(), instance.version())) {
            throw new WorkflowConflictException();
        }
        HandoverRequest finalized = workflowRepository.findHandoverRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if ("ACCEPTED".equals(finalized.status())) {
            updateInstanceAssignee(instance, request.targetIamUserId(), clock.instant());
            workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(request.targetIamUserId(), clock.instant()), clock.instant());
            workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).ifPresent(open -> workflowRepository.saveTask(new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED, open.candidateRole(), request.targetIamUserId(), request.targetIamUserId(), CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), clock.instant(),open.queueCode())));
            notifications.workflowAction(ticket, "HANDOVER_ACCEPTED", actor.iamUserId(), null);
        }
        record(actor, "HANDOVER_" + finalized.status(), ticketId, Map.of("handoverRequestId", requestId, "applicantIamUserId", request.applicantIamUserId(), "targetIamUserId", request.targetIamUserId()));
        return finalized;
    }

    /**
     * Co-handling is an opt-in assignment. The target must complete its own Flowable task and a
     * source-version check prevents a stale acceptance from gaining write access to a changed ticket.
     */
    @Transactional
    public CoHandlerRequest decideCoHandler(String ticketId, String requestId, String decision, String reason) {
        if (!Set.of("ACCEPTED", "REJECTED").contains(decision) || validText(reason, 1000) == null) throw new IllegalArgumentException("Co-handler decision is invalid");
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.UPDATE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
        CoHandlerRequest request = workflowRepository.findCoHandlerRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if (!"PENDING_CONFIRMATION".equals(request.status()) || !actor.iamUserId().equals(request.targetIamUserId()) || !hasSupport(actor)) {
            throw new AccessDeniedException("Co-handler decision is not authorized");
        }
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        workflowEngine.decideCoHandlerConfirmation(request.engineInstanceId(), actor.iamUserId(), decision);
        if (!workflowRepository.finalizeCoHandlerRequest(ticketId, requestId, decision, reason.trim(), clock.instant(), ticket.version(), instance.version())) {
            throw new WorkflowConflictException();
        }
        CoHandlerRequest finalized = workflowRepository.findCoHandlerRequest(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if ("ACCEPTED".equals(finalized.status())) {
            workflowRepository.addCoHandler(ticket.id(), request.targetIamUserId(), clock.instant());
            workflowRepository.addCoHandlerParticipant(ticket.id(), snapshot(request.targetIamUserId(), clock.instant()), clock.instant());
            notifications.workflowAction(ticket, "COHANDLER_ACCEPTED", actor.iamUserId(), null);
        }
        record(actor, "COHANDLER_" + finalized.status(), ticketId, Map.of("coHandlerRequestId", requestId,
            "applicantIamUserId", request.applicantIamUserId(), "targetIamUserId", request.targetIamUserId()));
        return finalized;
    }

    /**
     * Creates a short, ticket-scoped delegation for the current primary handler only. The server
     * resolves both identities from IAM projections and evaluates the validity window at every action.
     */
    @Transactional
    public TicketDelegation createDelegation(String ticketId, String delegateIamUserId, Instant effectiveUntil, String reason) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.UPDATE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
        if (!hasSupport(actor) || !actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || validText(reason, 500) == null) {
            throw new AccessDeniedException("Only the current primary handler may delegate this ticket");
        }
        requireActiveSupportTarget(delegateIamUserId);
        Instant now = clock.instant();
        if (actor.iamUserId().equals(delegateIamUserId) || effectiveUntil == null || !effectiveUntil.isAfter(now.plusSeconds(60))
            || effectiveUntil.isAfter(now.plus(java.time.Duration.ofDays(30)))) throw new IllegalArgumentException("Delegation window is invalid");
        TicketDelegation delegation = new TicketDelegation(UUID.randomUUID().toString(), ticketId, actor.iamUserId(), delegateIamUserId, now, effectiveUntil, now);
        workflowRepository.addDelegation(delegation);
        record(actor, "WORKFLOW_DELEGATION_CREATED", ticketId, Map.of("delegateIamUserId", delegateIamUserId, "effectiveUntil", effectiveUntil.toString()));
        notifications.workflowAction(ticket, "DELEGATION_CREATED", actor.iamUserId(), delegateIamUserId);
        return delegation;
    }

    /**
     * Reads live Flowable candidate tasks and then joins them to authorized ticket projections.
     * The workflow request projection alone is never accepted as proof that an approval remains open.
     */
    public ApprovalTaskInbox approvalInbox(int page, int pageSize) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        // This global endpoint is an approval worklist, not a way for ordinary support users
        // to probe whether any confirmation tasks exist. Targeted handover/co-handler
        // confirmations remain available from the already-authorized ticket detail view.
        if (actor.authorities().stream().noneMatch(APPROVAL_CANDIDATE_ROLES::contains)) {
            throw new AccessDeniedException("Approval inbox requires service-manager authority");
        }
        int offset = Math.multiplyExact(page - 1, pageSize);
        // Preserve the established, separately-tested multi-instance controlled-jump query.
        // The richer cross-process feed is appended only for other process keys, so an engine
        // correlation enhancement cannot accidentally hide the original approval worklist.
        List<WorkflowInboxTask> engineTasks = new java.util.ArrayList<>();
        workflowEngine.findPendingControlledJumpApprovalTasks(actor.iamUserId(), offset, pageSize).forEach(task ->
            workflowRepository.findJumpRequestById(task.approvalRequestId()).ifPresent(request ->
                engineTasks.add(new WorkflowInboxTask(task.engineTaskId(), "servicehubControlledJumpApproval", request.id(), request.ticketId(), task.createdAt()))));
        workflowEngine.findPendingInboxTasks(actor.iamUserId(), offset, pageSize).stream()
            .filter(task -> !"servicehubControlledJumpApproval".equals(task.processKey()))
            .forEach(engineTasks::add);
        List<ApprovalTaskInboxItem> items = engineTasks.stream()
            .map(task -> toApprovalInboxItem(actor, task)).flatMap(java.util.Optional::stream).toList();
        audit.publish(new AuditEvent(clock.instant(), requestId(), actor.iamUserId(), "APPROVAL_TASK_INBOX_READ", "workflow-approval-task", "collection",
            Map.of("returned", String.valueOf(items.size()), "page", String.valueOf(page))));
        return new ApprovalTaskInbox(items, page, pageSize);
    }

    private java.util.Optional<ApprovalTaskInboxItem> toApprovalInboxItem(CurrentUser actor, WorkflowInboxTask engineTask) {
        return ticketRepository.findById(engineTask.ticketId()).flatMap(ticket -> {
            try {
                // A worklist reveals only tasks Flowable already assigned to this user. It still
                // requires ordinary ticket read scope; the write endpoint rechecks APPROVE.
                authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.READ,
                    Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
                return switch (engineTask.processKey()) {
                    case "servicehubControlledJumpApproval" -> workflowRepository.findJumpRequestById(engineTask.requestId())
                        .filter(r -> "PENDING_APPROVAL".equals(r.status()) && r.approvalPolicy() != null
                            && r.approvalPolicy().candidateIamUserIds().contains(actor.iamUserId())
                            && approvalCandidates.isCurrentlyEligible(ticket.id(), r.approvalPolicy().candidateRoles(), actor.iamUserId(),
                                r.applicantIamUserId(), null))
                        .map(r -> new ApprovalTaskInboxItem("CONTROLLED_JUMP", r.id(), ticket.id(), ticket,
                            r.sourceNode() + "→" + r.targetNode(), r.reason(), r.createdAt(), engineTask.createdAt(),
                            r.applicantIamUserId(), r.sourceNode(), r.targetNode(), r.approvalPolicy().decisionMode(),
                            r.approvalPolicy().candidateIamUserIds().size(), "ALL_OF".equals(r.approvalPolicy().decisionMode()) ? r.approvalPolicy().candidateIamUserIds().size() : 1, true, null));
                    case "servicehubLifecycleActionApproval" -> lifecycleApprovalRepository.findById(engineTask.requestId())
                        .filter(r -> "PENDING_APPROVAL".equals(r.status()) && !actor.iamUserId().equals(r.applicantIamUserId())
                            && r.candidateIamUserIds().contains(actor.iamUserId())
                            && approvalCandidates.isCurrentlyEligible(ticket.id(), r.candidateRoles(), actor.iamUserId(),
                                r.applicantIamUserId(), r.targetIamUserId()))
                        .map(r -> new ApprovalTaskInboxItem("LIFECYCLE_ACTION", r.id(), ticket.id(), ticket,
                            r.action().name(), r.reason(), r.createdAt(), engineTask.createdAt(), r.applicantIamUserId(),
                            null, null, r.decisionMode(), r.candidateIamUserIds().size(), r.requiredApprovalCount(), true, null));
                    case "servicehubHandoverConfirmation" -> workflowRepository.findHandoverRequestById(engineTask.requestId())
                        .filter(r -> "PENDING_CONFIRMATION".equals(r.status()) && actor.iamUserId().equals(r.targetIamUserId()))
                        .map(r -> new ApprovalTaskInboxItem("HANDOVER_CONFIRMATION", r.id(), ticket.id(), ticket,
                            "HANDOVER", r.reason(), r.createdAt(), engineTask.createdAt(), r.applicantIamUserId(),
                            null, null, "ANY_ONE", 1, 1, true, null));
                    case "servicehubCoHandlerConfirmation" -> workflowRepository.findCoHandlerRequestById(engineTask.requestId())
                        .filter(r -> "PENDING_CONFIRMATION".equals(r.status()) && actor.iamUserId().equals(r.targetIamUserId()))
                        .map(r -> new ApprovalTaskInboxItem("COHANDLER_CONFIRMATION", r.id(), ticket.id(), ticket,
                            "ADD_COHANDLER", r.reason(), r.createdAt(), engineTask.createdAt(), r.applicantIamUserId(),
                            null, null, "ANY_ONE", 1, 1, true, null));
                    default -> java.util.Optional.empty();
                };
            } catch (AccessDeniedException ignored) { return java.util.Optional.empty(); }
        });
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
            workflowRepository.findOpenTask(ticketId, instance.currentNode()).ifPresent(old -> workflowRepository.saveTask(new WorkflowTask(old.id(), old.ticketId(), old.engineTaskId(), old.nodeKey(), WorkflowTaskStatus.CANCELLED, old.candidateRole(), old.candidateIamUserId(), old.assigneeIamUserId(), old.collaborationRole(), old.version() + 1, old.createdAt(), now,old.queueCode())));
            TicketStatus status = statusForNode(request.targetNode());
            if (!ticketRepository.updateStatus(ticketId, ticket.version(), status, now)) throw new WorkflowConflictException();
            WorkflowInstance changed = new WorkflowInstance(ticketId, instance.engineInstanceId(), moved.nodeKey(), status, null,
                instance.escalationLevel(), instance.primaryAssigneeIamUserId(), instance.processDefinitionId(), instance.processDefinitionVersion(),
                instance.version() + 1, instance.createdAt(), now);
            if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
            saveTask(task(ticketId, moved, candidateRoleForNode(request.targetNode()), candidateUserForNode(ticket, request.targetNode()), null, now));
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
        if (!approvalCandidates.isCurrentlyEligible(ticket.id(), request.approvalPolicy().candidateRoles(), approver.iamUserId(),
            request.applicantIamUserId(), null)) throw new AccessDeniedException("Approver is outside the current candidate scope");
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

    /**
     * A completed Flowable task is the only trigger for applying these actions.  There is no
     * execute endpoint and no browser-supplied target state, executor, or status.
     */
    @Transactional
    public LifecycleActionApprovalRequest decideLifecycleActionApproval(String ticketId, String requestId, String decision, String reason) {
        if (!Set.of("APPROVED", "REJECTED").contains(decision) || validText(reason, 1000) == null) throw new IllegalArgumentException("Approval decision is invalid");
        CurrentUser approver = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireAuthorized(approver, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.APPROVE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
        LifecycleActionApprovalRequest request = lifecycleApprovalRepository.find(ticketId, requestId).orElseThrow(WorkflowStateException::new);
        if (!"PENDING_APPROVAL".equals(request.status())) throw new WorkflowConflictException();
        if (approver.iamUserId().equals(request.applicantIamUserId()) || !request.candidateIamUserIds().contains(approver.iamUserId())
            || approver.authorities().stream().noneMatch(request.candidateRoles()::contains)) throw new AccessDeniedException("Approver is outside the frozen candidate scope");
        if (!approvalCandidates.isCurrentlyEligible(ticket.id(), request.candidateRoles(), approver.iamUserId(),
            request.applicantIamUserId(), request.targetIamUserId())) throw new AccessDeniedException("Approver is outside the current candidate scope");
        Instant now = clock.instant();
        var outcome = lifecycleApprovalEngine.decide(request.approvalEngineInstanceId(), approver.iamUserId(), decision);
        if (!outcome.processCompleted()) return request;
        if (!lifecycleApprovalRepository.finalizeDecision(ticketId, requestId, outcome.finalDecision(), approver.iamUserId(), reason.trim(), now)) throw new WorkflowConflictException();
        if ("APPROVED".equals(outcome.finalDecision())) {
            WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
            if (ticket.version() != request.sourceTicketVersion() || instance.version() != request.sourceWorkflowVersion()) {
                lifecycleApprovalRepository.markStale(ticketId, requestId, now);
            } else {
                Ticket applied = applyApprovedLifecycleAction(ticket, instance, request, now);
                if (!lifecycleApprovalRepository.markExecuted(ticketId, requestId, request.sourceTicketVersion(), request.sourceWorkflowVersion(), now)) throw new WorkflowConflictException();
                slaService.onTicketStateChanged(ticket, applied);
                notifications.workflowAction(applied, request.action().name(), request.applicantIamUserId(),
                    request.action() == WorkflowAction.ASSIGN ? request.targetIamUserId() : null);
            }
        }
        record(approver, "LIFECYCLE_APPROVAL_" + outcome.finalDecision(), ticketId, Map.of("approvalRequestId", requestId, "action", request.action().name()));
        return lifecycleApprovalRepository.find(ticketId, requestId).orElseThrow(WorkflowStateException::new);
    }

    private java.util.List<WorkflowAvailableAction> availableActions(CurrentUser actor, Ticket ticket, WorkflowInstance instance) {
        boolean support = hasSupport(actor);
        boolean requester = actor.iamUserId().equals(ticket.requester().iamUserId());
        boolean primary = actor.iamUserId().equals(instance.primaryAssigneeIamUserId());
        boolean manager = actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN");
        boolean handler = primary || workflowRepository.hasCoHandler(ticket.id(), actor.iamUserId()) || hasActiveDelegation(ticket, instance, actor, clock.instant());
        java.util.List<WorkflowAvailableAction> result = new java.util.ArrayList<>();
        switch (ticket.status()) {
            case SUBMITTED, PENDING_CLASSIFICATION -> { if (support) add(result, WorkflowAction.CLASSIFY, "分类", false); }
            case PENDING_ASSIGNMENT -> { if (support) add(result, WorkflowAction.ASSIGN, "分派", true); }
            case PENDING_ACCEPTANCE -> {
                if (support && instance.primaryAssigneeIamUserId() == null) add(result, WorkflowAction.CLAIM, "抢单", false);
                if (primary) add(result, WorkflowAction.ACCEPT, "受理", directAcceptRouting && nodeAssignments.requiresPreviousHandlerSelection(ticket.serviceCatalogItem().id(), "processing"));
            }
            case IN_PROGRESS -> {
                if (handler) add(result, WorkflowAction.REQUEST_USER_FEEDBACK, "解决并提交验证", false);
                if (primary || manager) {
                    add(result, WorkflowAction.TRANSFER, "转办", true);
                    add(result, WorkflowAction.ADD_COHANDLER, "添加协办", true);
                    add(result, WorkflowAction.HANDOVER, "交接班", true);
                    add(result, WorkflowAction.HOLD, "挂起", false);
                }
                if (support) add(result, WorkflowAction.ESCALATE, "升级", false);
            }
            case PENDING_USER_FEEDBACK -> {
                if (requester || handler) {
                    add(result, WorkflowAction.RESOLVE, "已解决，进入关闭", false);
                    add(result, WorkflowAction.START_PROCESSING, "未解决，退回处理", false);
                }
            }
            case RESOLVED -> { if (requester || handler) add(result, WorkflowAction.CLOSE, "确认关闭", false); }
            case ON_HOLD -> { if (primary || manager) add(result, WorkflowAction.RESUME, "恢复", false); }
            case CLOSED -> { if (requester || support) add(result, WorkflowAction.REOPEN, "重开", false); }
            default -> { }
        }
        if ((support || requester) && ticket.status() != TicketStatus.CLOSED && ticket.status() != TicketStatus.CANCELLED) add(result, WorkflowAction.CANCEL, "撤销", false);
        if (support && ticket.status() != TicketStatus.CLOSED && ticket.status() != TicketStatus.CANCELLED) {
            add(result, WorkflowAction.INTERNAL_COMMENT, "内部评论", false);
        }
        Set<String> pendingActions = lifecycleApprovalRepository.findByTicketId(ticket.id()).stream()
            .filter(request -> Set.of("PENDING_APPROVAL", "EXPIRING").contains(request.status()))
            .filter(request -> request.sourceTicketVersion() == ticket.version() && request.sourceWorkflowVersion() == instance.version())
            .map(request -> request.action().name()).collect(java.util.stream.Collectors.toSet());
        result.removeIf(action -> pendingActions.contains(action.code()));
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
            instance.escalationLevel(), assignedTarget, engine.processDefinitionId(), engine.processDefinitionVersion(),
            instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        if (action == WorkflowAction.ASSIGN) workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(assignedTarget, now), now);
        if (engine.taskId() != null) saveTask(task(ticket.id(), engine, transition.nextCandidateRole(), transition.nextCandidateUser(ticket, assignedTarget), null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket claim(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        assertSupport(actor); if (instance.primaryAssigneeIamUserId() != null) throw new WorkflowStateException();
        WorkflowTask open = workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).orElseThrow(WorkflowStateException::new);
        NodeAssignmentSnapshot routing=nodeAssignments.latestSnapshot(ticket.id(),instance.currentNode());
        if(open.queueCode()!=null)queueEligibility.requireEligible(open.queueCode(),actor,Set.of(open.candidateRole()),ticketContexts.resolveForScope(ticket.id()));
        else if(routing!=null&&routing.mode()==cn.servicehub.workflow.routing.NodeAssignmentMode.SHARED_QUEUE)throw new AccessDeniedException("Shared queue task has no durable queue");
        if(!workflowRepository.claimOpenTask(open.id(),open.version(),open.queueCode(),actor.iamUserId(),now))throw new WorkflowConflictException();
        updateInstanceAssignee(instance, actor.iamUserId(), now); workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(actor.iamUserId(), now), now); return ticket;
    }

    private Ticket transfer(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, Instant now) {
        assertPrimaryOrManager(actor, instance);
        if (ticket.status() != TicketStatus.IN_PROGRESS || !"processing".equals(instance.currentNode())) throw new WorkflowStateException();
        boolean eligibleTarget = nodeAssignments.candidates(ticket.id(), ticket.serviceCatalogItem().id(), instance.currentNode(), ticket.requester().iamUserId()).stream()
            .anyMatch(candidate -> candidate.iamUserId().equals(target));
        if (!eligibleTarget || target.equals(instance.primaryAssigneeIamUserId())) throw new IllegalArgumentException("Transfer target is outside the active routing pool");
        updateInstanceAssignee(instance, target, now); workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(target, now), now);
        workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).ifPresent(open -> workflowRepository.saveTask(new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED, open.candidateRole(), target, target, CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), now,open.queueCode())));
        return ticket;
    }

    private Ticket requestHandover(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, String reason, Instant now) {
        assertPrimaryOrManager(actor, instance);
        requireActiveSupportTarget(target);
        if (target.equals(instance.primaryAssigneeIamUserId()) || validText(reason, 1000) == null) throw new IllegalArgumentException("Handover request is invalid");
        if (workflowRepository.findHandoverRequests(ticket.id()).stream().anyMatch(item -> "PENDING_CONFIRMATION".equals(item.status()))) {
            throw new WorkflowStateException();
        }
        String requestId = UUID.randomUUID().toString();
        var definition = workflowEngine.resolveHandoverConfirmationDefinition();
        var engine = workflowEngine.startHandoverConfirmation(requestId, ticket.id(), actor.iamUserId(), target, definition);
        workflowRepository.addHandoverRequest(new HandoverRequest(requestId, ticket.id(), engine.instanceId(), definition.processDefinitionId(), definition.version(),
            actor.iamUserId(), target, reason.trim(), "PENDING_CONFIRMATION", ticket.version(), instance.version(), null, null, now));
        return ticket;
    }

    private Ticket requestCoHandler(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, String reason, Instant now) {
        assertPrimaryOrManager(actor, instance); requireActiveSupportTarget(target);
        if (target.equals(actor.iamUserId()) || target.equals(instance.primaryAssigneeIamUserId()) || workflowRepository.hasCoHandler(ticket.id(), target)
            || validText(reason, 1000) == null) throw new IllegalArgumentException("Co-handler request is invalid");
        if (workflowRepository.findCoHandlerRequests(ticket.id()).stream().anyMatch(item -> "PENDING_CONFIRMATION".equals(item.status()) && target.equals(item.targetIamUserId()))) {
            throw new WorkflowStateException();
        }
        String requestId = UUID.randomUUID().toString();
        var definition = workflowEngine.resolveCoHandlerConfirmationDefinition();
        var engine = workflowEngine.startCoHandlerConfirmation(requestId, ticket.id(), actor.iamUserId(), target, definition);
        workflowRepository.addCoHandlerRequest(new CoHandlerRequest(requestId, ticket.id(), engine.instanceId(), definition.processDefinitionId(), definition.version(),
            actor.iamUserId(), target, reason.trim(), "PENDING_CONFIRMATION", ticket.version(), instance.version(), null, null, now));
        return ticket;
    }

    private Ticket comment(Ticket ticket, CurrentUser actor, String comment, Instant now) {
        if (comment == null || comment.isBlank() || comment.length() > 2000) throw new IllegalArgumentException("Comment is invalid");
        workflowRepository.addComment(new WorkflowComment(UUID.randomUUID().toString(), ticket.id(), actor.iamUserId(), comment.trim(), now)); return ticket;
    }

    private Ticket requestJump(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String targetNode, String reason, Instant now) {
        Set<String> allowedTargets = directAcceptRouting ? Set.of("accept", "processing", "user_feedback", "closure") : Set.of("classify", "assign", "accept", "processing", "user_feedback", "closure");
        assertSupport(actor); if (!allowedTargets.contains(targetNode) || validText(reason, 1000) == null) throw new IllegalArgumentException("Jump request is invalid");
        String requestId = UUID.randomUUID().toString();
        var definition = workflowEngine.resolveControlledJumpApprovalDefinition();
        // Never create a self-approval task. In ALL_OF mode including the applicant would make
        // an otherwise valid request impossible to finish, while in ANY_ONE it would expose an
        // unnecessary forbidden task. The immutable snapshot records the post-exclusion set.
        Set<String> candidateUsers = approvalCandidates.resolve(ticket.id(), APPROVAL_CANDIDATE_ROLES, actor.iamUserId(), null);
        if (candidateUsers.isEmpty()) throw new WorkflowStateException();
        String decisionMode = approvalPolicyResolver.decisionModeFor(targetNode);
        ApprovalPolicySnapshot policy = new ApprovalPolicySnapshot(definition.processKey(), definition.processDefinitionId(), definition.version(),
            APPROVAL_CANDIDATE_ROLES, decisionMode, NO_POLICY_VERSION, NO_POLICY_VERSION, now, candidateUsers);
        var approval = workflowEngine.startControlledJumpApproval(requestId, ticket.id(), actor.iamUserId(), definition, candidateUsers, decisionMode);
        workflowRepository.addJumpRequest(new ControlledJumpRequest(requestId, ticket.id(), actor.iamUserId(), instance.currentNode(), targetNode,
            reason.trim(), "PENDING_APPROVAL", now, null, null, null, approval.instanceId(), ticket.version(), instance.version(),
            null, null, null, null, null, null, policy)); return ticket;
    }

    private Ticket requestLifecycleActionApproval(Ticket ticket, WorkflowInstance instance, CurrentUser actor, WorkflowAction action,
                                                  String requestedTargetIamUserId, String reason, Instant now) {
        String effectiveReason = validText(reason, 1000);
        if (effectiveReason == null) effectiveReason = switch (action) {
            case ACCEPT -> "受理工单";
            case CLOSE -> "确认关闭工单";
            default -> throw new IllegalArgumentException("Lifecycle action reason is required");
        };
        String frozenTargetIamUserId = switch (action) {
            case HOLD -> { assertPrimaryOrManager(actor, instance); if (ticket.status() != TicketStatus.IN_PROGRESS) throw new WorkflowStateException(); yield null; }
            case ESCALATE -> { assertSupport(actor); if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED) throw new WorkflowStateException(); yield null; }
            case CANCEL -> { if (!(actor.iamUserId().equals(ticket.requester().iamUserId()) || hasSupport(actor)) || ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED) throw new WorkflowStateException(); yield null; }
            case REOPEN -> { if (!(actor.iamUserId().equals(ticket.requester().iamUserId()) || hasSupport(actor)) || ticket.status() != TicketStatus.CLOSED) throw new WorkflowStateException(); yield null; }
            case ASSIGN -> {
                assertSupport(actor);
                if (ticket.status() != TicketStatus.PENDING_ASSIGNMENT) throw new WorkflowStateException();
                boolean eligibleTarget = nodeAssignments.candidates(ticket.id(), ticket.serviceCatalogItem().id(), "accept", ticket.requester().iamUserId()).stream()
                    .anyMatch(candidate -> candidate.iamUserId().equals(requestedTargetIamUserId));
                if (!eligibleTarget) throw new IllegalArgumentException("Assignment target is outside the active routing pool");
                yield requestedTargetIamUserId;
            }
            case ACCEPT -> {
                if (ticket.status() != TicketStatus.PENDING_ACCEPTANCE || !actor.iamUserId().equals(instance.primaryAssigneeIamUserId())) throw new AccessDeniedException("Only the current primary handler may accept this ticket");
                yield directAcceptRouting ? nodeAssignments.resolveNextHandler(ticket.id(), ticket.serviceCatalogItem().id(), "processing", ticket.requester().iamUserId(), requestedTargetIamUserId).selectedIamUserId() : null;
            }
            case RESOLVE -> {
                if (ticket.status() != TicketStatus.PENDING_USER_FEEDBACK) throw new WorkflowStateException();
                assertHandler(actor, ticket, instance, action);
                yield null;
            }
            case CLOSE -> {
                if (ticket.status() != TicketStatus.RESOLVED) throw new WorkflowStateException();
                assertHandler(actor, ticket, instance, action);
                yield null;
            }
            default -> throw new IllegalArgumentException("Lifecycle approval action is invalid");
        };
        // An ASSIGN target must not approve a request that makes the target the primary handler.
        // This complements the mandatory applicant self-approval exclusion for every action.
        var policy = lifecycleApprovalPolicyResolver.resolve(ticket, action, actor.iamUserId(), frozenTargetIamUserId, now);
        String requestId = UUID.randomUUID().toString();
        var definition = lifecycleApprovalEngine.resolveDefinition();
        var engine = lifecycleApprovalEngine.start(requestId, ticket.id(), actor.iamUserId(), definition, policy.candidateIamUserIds(), policy.policy().decisionMode(), policy.requiredApprovalCount());
        lifecycleApprovalRepository.save(new LifecycleActionApprovalRequest(requestId, ticket.id(), action, actor.iamUserId(), effectiveReason.trim(), frozenTargetIamUserId, ticket.version(), instance.version(),
            engine.instanceId(), definition.processKey(), definition.processDefinitionId(), definition.version(), policy.policy().id(), policy.policy().version(), policy.policy().decisionMode(), policy.requiredApprovalCount(),
            policy.policy().timeoutPolicyVersion(), policy.policy().escalationPolicyVersion(), policy.dueAt(), policy.policy().candidateRoles(), policy.candidateIamUserIds(), "PENDING_APPROVAL", null, null, null, null, now));
        return ticket;
    }

    private Ticket applyApprovedLifecycleAction(Ticket ticket, WorkflowInstance instance, LifecycleActionApprovalRequest request, Instant now) {
        return switch (request.action()) {
            case HOLD -> { if (ticket.status() != TicketStatus.IN_PROGRESS) throw new WorkflowStateException(); yield updateStatusAndInstance(ticket, instance, TicketStatus.ON_HOLD, ticket.status(), instance.escalationLevel(), now); }
            case ESCALATE -> { if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED) throw new WorkflowStateException(); yield updateStatusAndInstance(ticket, instance, ticket.status(), instance.resumeStatus(), instance.escalationLevel() + 1, now); }
            case CANCEL -> { if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED) throw new WorkflowStateException(); workflowEngine.cancel(instance.engineInstanceId(), "ticket-cancelled-after-approval"); yield updateStatusAndInstance(ticket, instance, TicketStatus.CANCELLED, null, instance.escalationLevel(), now); }
            case REOPEN -> { if (ticket.status() != TicketStatus.CLOSED) throw new WorkflowStateException(); yield reopenApproved(ticket, instance, now); }
            case ASSIGN, ACCEPT, RESOLVE, CLOSE -> applyApprovedLifecycleAdvance(ticket, instance, request, now);
            default -> throw new WorkflowStateException();
        };
    }

    /**
     * Applies a lifecycle transition using only the immutable approval snapshot.  In particular,
     * ASSIGN never reads a target from the completing approver's request and it validates that
     * the frozen target is still an active support user before the Flowable task is advanced.
     */
    private Ticket applyApprovedLifecycleAdvance(Ticket ticket, WorkflowInstance instance, LifecycleActionApprovalRequest request, Instant now) {
        WorkflowAction action = request.action();
        Transition transition = Transition.forAction(ticket.status(), action);
        String assignedTarget = action == WorkflowAction.ASSIGN || (directAcceptRouting && action == WorkflowAction.ACCEPT)
            ? requireActiveSupportTargetAndReturn(request.targetIamUserId()) : instance.primaryAssigneeIamUserId();
        WorkflowEngineInstance engine = workflowEngine.advance(instance.engineInstanceId(), transition.expectedNode());
        finishOpenTask(ticket.id(), transition.expectedNode(), request.applicantIamUserId(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), transition.nextStatus(), now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), transition.nextStatus(), null,
            instance.escalationLevel(), assignedTarget, engine.processDefinitionId(), engine.processDefinitionVersion(),
            instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        if (action == WorkflowAction.ASSIGN || (directAcceptRouting && action == WorkflowAction.ACCEPT)) workflowRepository.replacePrimaryParticipant(ticket.id(), snapshot(assignedTarget, now), now);
        if (engine.taskId() != null) saveTask(task(ticket.id(), engine, transition.nextCandidateRole(), transition.nextCandidateUser(ticket, assignedTarget), null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket reopenApproved(Ticket ticket, WorkflowInstance instance, Instant now) {
        WorkflowEngineInstance engine = workflowEngine.start(ticket.id()); workflowRepository.clearPrimaryParticipant(ticket.id(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), TicketStatus.PENDING_CLASSIFICATION, now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.PENDING_CLASSIFICATION, null,
            instance.escalationLevel(), null, engine.processDefinitionId(), engine.processDefinitionVersion(), instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        saveTask(task(ticket.id(), engine, "ROLE_FIRST_LINE_SUPPORT", null, null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private boolean isLifecycleApprovalAction(WorkflowAction action) {
        return Set.of(WorkflowAction.HOLD, WorkflowAction.ESCALATE, WorkflowAction.CANCEL, WorkflowAction.REOPEN,
            WorkflowAction.ASSIGN, WorkflowAction.ACCEPT, WorkflowAction.RESOLVE, WorkflowAction.CLOSE).contains(action);
    }

    private boolean requiresLifecycleApproval(Ticket ticket, WorkflowAction action) {
        if (!isLifecycleApprovalAction(action)) return false;
        return !Set.of(WorkflowAction.ACCEPT, WorkflowAction.RESOLVE, WorkflowAction.CLOSE).contains(action)
            || lifecycleApprovalPolicyResolver.findApplicable(ticket, action).isPresent();
    }

    private boolean hasActiveLifecycleApproval(Ticket ticket, WorkflowInstance instance, WorkflowAction action) {
        if (!isLifecycleApprovalAction(action)) return false;
        return lifecycleApprovalRepository.findByTicketId(ticket.id()).stream()
            .anyMatch(request -> request.action() == action
                && request.sourceTicketVersion() == ticket.version()
                && request.sourceWorkflowVersion() == instance.version()
                && Set.of("PENDING_APPROVAL", "EXPIRING").contains(request.status()));
    }

    private Ticket returnToProcessing(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        boolean requester = actor.iamUserId().equals(ticket.requester().iamUserId());
        if (ticket.status() != TicketStatus.PENDING_USER_FEEDBACK) throw new WorkflowStateException();
        if (!requester) assertHandler(actor, ticket, instance, WorkflowAction.START_PROCESSING);
        WorkflowEngineInstance engine = workflowEngine.moveControlledActivity(instance.engineInstanceId(), "user_feedback", "processing");
        finishOpenTask(ticket.id(), "user_feedback", actor.iamUserId(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), TicketStatus.IN_PROGRESS, now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.IN_PROGRESS, null,
            instance.escalationLevel(), instance.primaryAssigneeIamUserId(), engine.processDefinitionId(), engine.processDefinitionVersion(),
            instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        if (engine.taskId() != null) workflowRepository.saveTask(new WorkflowTask(UUID.randomUUID().toString(), ticket.id(), engine.taskId(), engine.nodeKey(),
            WorkflowTaskStatus.OPEN, "ROLE_FIRST_LINE_SUPPORT", instance.primaryAssigneeIamUserId(), null, null, 0, now, now, null));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
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
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), TicketStatus.PENDING_CLASSIFICATION,
            null, instance.escalationLevel(), null, engine.processDefinitionId(), engine.processDefinitionVersion(),
            instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        saveTask(task(ticket.id(), engine, "ROLE_FIRST_LINE_SUPPORT", null, null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket updateStatusAndInstance(Ticket ticket, WorkflowInstance instance, TicketStatus status, TicketStatus resumeStatus, int escalationLevel, Instant now) {
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), status, now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), instance.engineInstanceId(), instance.currentNode(), status, resumeStatus,
            escalationLevel, instance.primaryAssigneeIamUserId(), instance.processDefinitionId(), instance.processDefinitionVersion(),
            instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private void finishOpenTask(String ticketId, String node, String actor, Instant now) { workflowRepository.findOpenTask(ticketId, node).ifPresent(task -> workflowRepository.saveTask(new WorkflowTask(task.id(), task.ticketId(), task.engineTaskId(), task.nodeKey(), WorkflowTaskStatus.COMPLETED, task.candidateRole(), task.candidateIamUserId(), actor, task.collaborationRole(), task.version() + 1, task.createdAt(), now,task.queueCode()))); }
    private void updateInstanceAssignee(WorkflowInstance instance, String assignee, Instant now) { if (!workflowRepository.updateInstance(new WorkflowInstance(instance.ticketId(), instance.engineInstanceId(), instance.currentNode(), instance.status(), instance.resumeStatus(), instance.escalationLevel(), assignee, instance.processDefinitionId(), instance.processDefinitionVersion(), instance.version() + 1, instance.createdAt(), now), instance.version())) throw new WorkflowConflictException(); }
    private WorkflowTask task(String ticketId, WorkflowEngineInstance engine, String role, String user, CollaborationRole collaborationRole, Instant now) {NodeAssignmentSnapshot route=nodeAssignments.latestSnapshot(ticketId,engine.nodeKey());return task(ticketId,engine,role,user,collaborationRole,now,route==null?null:route.queueCode());}
    private WorkflowTask task(String ticketId,WorkflowEngineInstance engine,String role,String user,CollaborationRole collaborationRole,Instant now,String queueCode){return new WorkflowTask(UUID.randomUUID().toString(),ticketId,engine.taskId(),engine.nodeKey(),WorkflowTaskStatus.OPEN,role,user,null,collaborationRole,0,now,now,queueCode);}
    private void saveTask(WorkflowTask task){workflowRepository.saveTask(task);captureQueueSnapshot(task);}
    private void captureQueueSnapshot(WorkflowTask task){if(task.queueCode()==null)return;NodeAssignmentSnapshot route=nodeAssignments.latestSnapshot(task.ticketId(),task.nodeKey());if(route==null)return;var queue=supportQueues.findByCode(task.queueCode()).orElseThrow(WorkflowStateException::new);var context=ticketContexts.resolveForScope(task.ticketId());Set<String> candidates=queueEligibility.eligibleMembers(queue.code(),route.candidateRoles(),context,context.requesterIamUserId());supportQueues.saveRoutingSnapshot(new WorkflowQueueRoutingSnapshot(UUID.randomUUID().toString(),task.ticketId(),task.id(),task.nodeKey(),queue.code(),new WorkflowQueueRoutingSnapshot.NodeAssignmentEvidence(route.mode().name(),route.policyVersion()),queue.version(),queueEligibility.scopeDigest(queue),candidates,queueEligibility.contextDigest(context),clock.instant()));}
    private void requireTicketAction(CurrentUser actor, Ticket ticket, WorkflowAction action) { ObjectAction objectAction = action == WorkflowAction.INTERNAL_COMMENT ? ObjectAction.COMMENT : (action == WorkflowAction.TRANSFER || action == WorkflowAction.HANDOVER ? ObjectAction.TRANSFER : action == WorkflowAction.ASSIGN ? ObjectAction.ASSIGN : ObjectAction.UPDATE); authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), objectAction, Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id()))); }
    private void requireControlledJumpManager(CurrentUser actor, Ticket ticket) {
        if (!isControlledJumpManager(actor)) throw new AccessDeniedException("Controlled jump management requires service-manager authority");
        authorizationService.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.APPROVE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
    }
    private boolean isControlledJumpManager(CurrentUser actor) { return actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN"); }
    private void assertHandler(CurrentUser actor, Ticket ticket, WorkflowInstance instance, WorkflowAction action) { if ((action == WorkflowAction.RESOLVE || action == WorkflowAction.CLOSE) && actor.iamUserId().equals(ticket.requester().iamUserId())) return; if (action == WorkflowAction.CLASSIFY || action == WorkflowAction.ASSIGN) { assertSupport(actor); return; } if (action == WorkflowAction.ACCEPT && actor.iamUserId().equals(instance.primaryAssigneeIamUserId())) return; if (instance.primaryAssigneeIamUserId() != null && (actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || workflowRepository.hasCoHandler(ticket.id(), actor.iamUserId()) || hasActiveDelegation(ticket, instance, actor, clock.instant()))) return; throw new AccessDeniedException("Only a server-resolved handler may process this ticket"); }
    private boolean hasActiveDelegation(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) { return hasSupport(actor) && instance.primaryAssigneeIamUserId() != null && workflowRepository.hasActiveDelegation(ticket.id(), instance.primaryAssigneeIamUserId(), actor.iamUserId(), now); }
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
