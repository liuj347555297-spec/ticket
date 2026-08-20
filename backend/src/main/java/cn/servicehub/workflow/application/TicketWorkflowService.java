package cn.servicehub.workflow.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.notification.application.NotificationService;
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
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import cn.servicehub.workflow.engine.WorkflowEngineInstance;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
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
    private final TicketWorkflowRepository workflowRepository;
    private final TicketRepository ticketRepository;
    private final WorkflowEnginePort workflowEngine;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectAuthorizationService authorizationService;
    private final IamUserProjectionRepository iamUsers;
    private final AuditEventPublisher audit;
    private final NotificationService notifications;
    private final Clock clock = Clock.systemUTC();

    public TicketWorkflowService(TicketWorkflowRepository workflowRepository, TicketRepository ticketRepository,
                                 WorkflowEnginePort workflowEngine, CurrentUserProvider currentUserProvider,
                                 ObjectAuthorizationService authorizationService, IamUserProjectionRepository iamUsers,
                                 AuditEventPublisher audit, NotificationService notifications) {
        this.workflowRepository = workflowRepository; this.ticketRepository = ticketRepository; this.workflowEngine = workflowEngine;
        this.currentUserProvider = currentUserProvider; this.authorizationService = authorizationService; this.iamUsers = iamUsers; this.audit = audit; this.notifications = notifications;
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
        record(actor, "WORKFLOW_" + command.action().name(), ticketId, Map.of("from", ticket.status().name(), "to", result.status().name()));
        notifications.workflowAction(result, command.action().name(), actor.iamUserId(), notificationTarget(command));
        return result;
    }

    public WorkflowOverview overview(String ticketId) {
        // The controller first resolves the ticket through TicketService, including object-level read authorization.
        WorkflowInstance instance = workflowRepository.findInstance(ticketId).orElseThrow(WorkflowStateException::new);
        return new WorkflowOverview(instance, workflowRepository.findTasks(ticketId), workflowRepository.findComments(ticketId));
    }

    private Ticket advanceLifecycle(Ticket ticket, WorkflowInstance instance, CurrentUser actor, WorkflowActionCommand command, Instant now) {
        WorkflowAction action = command.action();
        Transition transition = Transition.forAction(ticket.status(), action);
        assertHandler(actor, ticket, instance, action);
        String assignedTarget = action == WorkflowAction.ASSIGN ? requireActiveTargetAndReturn(command.targetIamUserId()) : instance.primaryAssigneeIamUserId();
        WorkflowEngineInstance engine = workflowEngine.advance(instance.engineInstanceId(), transition.expectedNode());
        finishOpenTask(ticket.id(), transition.expectedNode(), actor.iamUserId(), now);
        if (!ticketRepository.updateStatus(ticket.id(), ticket.version(), transition.nextStatus(), now)) throw new WorkflowConflictException();
        WorkflowInstance changed = new WorkflowInstance(ticket.id(), engine.instanceId(), engine.nodeKey(), transition.nextStatus(), null,
            instance.escalationLevel(), assignedTarget, instance.version() + 1, instance.createdAt(), now);
        if (!workflowRepository.updateInstance(changed, instance.version())) throw new WorkflowConflictException();
        if (engine.taskId() != null) workflowRepository.saveTask(task(ticket.id(), engine, transition.nextCandidateRole(), transition.nextCandidateUser(ticket, assignedTarget), null, now));
        return ticketRepository.findById(ticket.id()).orElseThrow(() -> new TicketNotFoundException(ticket.id()));
    }

    private Ticket claim(Ticket ticket, WorkflowInstance instance, CurrentUser actor, Instant now) {
        assertSupport(actor); if (instance.primaryAssigneeIamUserId() != null) throw new WorkflowStateException();
        WorkflowTask open = workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).orElseThrow(WorkflowStateException::new);
        WorkflowTask claimed = new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED,
            open.candidateRole(), actor.iamUserId(), actor.iamUserId(), CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), now);
        workflowRepository.saveTask(claimed); updateInstanceAssignee(instance, actor.iamUserId(), now); return ticket;
    }

    private Ticket transfer(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, Instant now) {
        assertPrimaryOrManager(actor, instance); requireActiveTarget(target); updateInstanceAssignee(instance, target, now);
        workflowRepository.findOpenTask(ticket.id(), instance.currentNode()).ifPresent(open -> workflowRepository.saveTask(new WorkflowTask(open.id(), open.ticketId(), open.engineTaskId(), open.nodeKey(), WorkflowTaskStatus.CLAIMED, open.candidateRole(), target, target, CollaborationRole.PRIMARY, open.version() + 1, open.createdAt(), now)));
        return ticket;
    }

    private Ticket coHandle(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String target, Instant now) {
        assertPrimaryOrManager(actor, instance); requireActiveTarget(target); workflowRepository.addCoHandler(ticket.id(), target, now); return ticket;
    }

    private Ticket comment(Ticket ticket, CurrentUser actor, String comment, Instant now) {
        if (comment == null || comment.isBlank() || comment.length() > 2000) throw new IllegalArgumentException("Comment is invalid");
        workflowRepository.addComment(new WorkflowComment(UUID.randomUUID().toString(), ticket.id(), actor.iamUserId(), comment.trim(), now)); return ticket;
    }

    private Ticket requestJump(Ticket ticket, WorkflowInstance instance, CurrentUser actor, String targetNode, String reason, Instant now) {
        assertSupport(actor); if (!Set.of("classify", "assign", "accept", "processing", "user_feedback", "closure").contains(targetNode) || validText(reason, 1000) == null) throw new IllegalArgumentException("Jump request is invalid");
        workflowRepository.addJumpRequest(new ControlledJumpRequest(UUID.randomUUID().toString(), ticket.id(), actor.iamUserId(), instance.currentNode(), targetNode, reason.trim(), "PENDING_APPROVAL", now)); return ticket;
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
    private void assertHandler(CurrentUser actor, Ticket ticket, WorkflowInstance instance, WorkflowAction action) { if (action == WorkflowAction.CLOSE && actor.iamUserId().equals(ticket.requester().iamUserId())) return; if (action == WorkflowAction.CLASSIFY || action == WorkflowAction.ASSIGN) { assertSupport(actor); return; } if (action == WorkflowAction.ACCEPT && actor.iamUserId().equals(instance.primaryAssigneeIamUserId())) return; if (instance.primaryAssigneeIamUserId() != null && (actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || workflowRepository.hasCoHandler(ticket.id(), actor.iamUserId()))) return; throw new AccessDeniedException("Only a server-resolved handler may process this ticket"); }
    private void assertPrimaryOrManager(CurrentUser actor, WorkflowInstance instance) { if (hasSupport(actor) && (actor.iamUserId().equals(instance.primaryAssigneeIamUserId()) || actor.authorities().contains("ROLE_SERVICE_MANAGER") || actor.authorities().contains("ROLE_PLATFORM_ADMIN"))) return; throw new AccessDeniedException("Only primary handler or manager may perform this action"); }
    private void assertSupport(CurrentUser actor) { if (!hasSupport(actor)) throw new AccessDeniedException("Support role is required"); }
    private boolean hasSupport(CurrentUser actor) { return actor.authorities().stream().anyMatch(SUPPORT::contains); }
    private void requireActiveTarget(String target) { requireActiveTargetAndReturn(target); }
    private String requireActiveTargetAndReturn(String target) { if (target == null || iamUsers.findActiveByIamUserId(target).isEmpty()) throw new IllegalArgumentException("Target IAM user is unavailable"); return target; }
    private String validText(String text, int max) { return text == null || text.isBlank() || text.length() > max ? null : text; }
    private String notificationTarget(WorkflowActionCommand command) {
        return switch (command.action()) {
            case ASSIGN, TRANSFER, HANDOVER, ADD_COHANDLER -> command.targetIamUserId();
            default -> null;
        };
    }
    private void record(CurrentUser actor, String action, String ticketId, Map<String, String> detail) { String requestId = MDC.get("requestId") == null ? "system" : MDC.get("requestId"); Instant now = clock.instant(); workflowRepository.appendEvent(ticketId, action, actor.iamUserId(), requestId, detail, now); audit.publish(new AuditEvent(now, requestId, actor.iamUserId(), action, "ticket", ticketId, detail)); }

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
