package cn.servicehub.workflow.infrastructure;

import cn.servicehub.workflow.domain.ControlledJumpRequest;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryTicketWorkflowRepository implements TicketWorkflowRepository {
    private final ConcurrentHashMap<String, WorkflowInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> coHandlers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowParticipant>> participants = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<WorkflowComment> comments = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ControlledJumpRequest> jumpRequests = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<cn.servicehub.workflow.domain.ApprovalDecisionRecord> approvalDecisions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowEvent> events = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong eventSequence = new java.util.concurrent.atomic.AtomicLong();

    @Override public void create(WorkflowInstance instance, WorkflowTask initialTask) {
        if (instances.putIfAbsent(instance.ticketId(), instance) != null) throw new IllegalStateException("Workflow already exists");
        tasks.put(initialTask.id(), initialTask);
    }
    @Override public Optional<WorkflowInstance> findInstance(String ticketId) { return Optional.ofNullable(instances.get(ticketId)); }
    @Override public boolean updateInstance(WorkflowInstance replacement, long expectedVersion) {
        AtomicBoolean updated = new AtomicBoolean(false);
        instances.computeIfPresent(replacement.ticketId(), (ignored, current) -> {
            if (current.version() != expectedVersion) return current;
            updated.set(true); return replacement;
        });
        return updated.get();
    }
    @Override public Optional<WorkflowTask> findOpenTask(String ticketId, String nodeKey) {
        return tasks.values().stream().filter(task -> task.ticketId().equals(ticketId) && task.nodeKey().equals(nodeKey)
                && (task.status() == WorkflowTaskStatus.OPEN || task.status() == WorkflowTaskStatus.CLAIMED))
            .findFirst();
    }
    @Override public void saveTask(WorkflowTask task) { tasks.put(task.id(), task); }
    @Override public List<WorkflowTask> findTasks(String ticketId) { return tasks.values().stream().filter(t -> t.ticketId().equals(ticketId)).sorted(Comparator.comparing(WorkflowTask::createdAt)).toList(); }
    @Override public List<String> findTodoTicketIds(String iamUserId, java.util.Set<String> authorities) {
        return tasks.values().stream().filter(task -> task.status() == WorkflowTaskStatus.OPEN || task.status() == WorkflowTaskStatus.CLAIMED)
            .filter(task -> iamUserId.equals(task.assigneeIamUserId()) || (task.assigneeIamUserId() == null && (iamUserId.equals(task.candidateIamUserId()) || authorities.contains(task.candidateRole()))))
            .map(WorkflowTask::ticketId).distinct().toList();
    }
    @Override public List<String> findCompletedTicketIds(String iamUserId) {
        return tasks.values().stream().filter(task -> task.status() == WorkflowTaskStatus.COMPLETED && iamUserId.equals(task.assigneeIamUserId()))
            .map(WorkflowTask::ticketId).distinct().toList();
    }
    @Override public void addCoHandler(String ticketId, String iamUserId, Instant at) { coHandlers.computeIfAbsent(ticketId, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(iamUserId); }
    @Override public boolean hasCoHandler(String ticketId, String iamUserId) { return coHandlers.getOrDefault(ticketId, new CopyOnWriteArrayList<>()).contains(iamUserId); }
    @Override public void replacePrimaryParticipant(String ticketId, cn.servicehub.ticket.domain.IdentitySnapshot identity, Instant at) {
        CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowParticipant> rows = participants.computeIfAbsent(ticketId, ignored -> new CopyOnWriteArrayList<>());
        rows.removeIf(value -> value.role() == cn.servicehub.workflow.domain.CollaborationRole.PRIMARY);
        rows.add(new cn.servicehub.workflow.domain.WorkflowParticipant(ticketId, cn.servicehub.workflow.domain.CollaborationRole.PRIMARY, identity, at));
    }
    @Override public void addCoHandlerParticipant(String ticketId, cn.servicehub.ticket.domain.IdentitySnapshot identity, Instant at) {
        CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowParticipant> rows = participants.computeIfAbsent(ticketId, ignored -> new CopyOnWriteArrayList<>());
        rows.removeIf(value -> value.role() == cn.servicehub.workflow.domain.CollaborationRole.CO_HANDLER && value.identity().iamUserId().equals(identity.iamUserId()));
        rows.add(new cn.servicehub.workflow.domain.WorkflowParticipant(ticketId, cn.servicehub.workflow.domain.CollaborationRole.CO_HANDLER, identity, at));
    }
    @Override public void clearPrimaryParticipant(String ticketId, Instant at) { participants.getOrDefault(ticketId, new CopyOnWriteArrayList<>()).removeIf(value -> value.role() == cn.servicehub.workflow.domain.CollaborationRole.PRIMARY); }
    @Override public List<cn.servicehub.workflow.domain.WorkflowParticipant> findActiveParticipants(String ticketId) { return List.copyOf(participants.getOrDefault(ticketId, new CopyOnWriteArrayList<>())); }
    @Override public void addComment(WorkflowComment comment) { comments.add(comment); }
    @Override public List<WorkflowComment> findComments(String ticketId) { return comments.stream().filter(c -> c.ticketId().equals(ticketId)).sorted(Comparator.comparing(WorkflowComment::createdAt)).toList(); }
    @Override public void addJumpRequest(ControlledJumpRequest request) { jumpRequests.add(request); }
    @Override public List<ControlledJumpRequest> findJumpRequests(String ticketId) { return jumpRequests.stream().filter(item -> item.ticketId().equals(ticketId)).sorted(Comparator.comparing(ControlledJumpRequest::createdAt)).toList(); }
    @Override public Optional<ControlledJumpRequest> findJumpRequest(String ticketId, String requestId) { return jumpRequests.stream().filter(item -> item.ticketId().equals(ticketId) && item.id().equals(requestId)).findFirst(); }
    @Override public Optional<ControlledJumpRequest> findJumpRequestById(String requestId) { return jumpRequests.stream().filter(item -> item.id().equals(requestId)).findFirst(); }
    @Override public boolean finalizeJumpRequestApproval(String ticketId, String requestId, String decision, String approver, String reason, Instant at) { for (int i = 0; i < jumpRequests.size(); i++) { ControlledJumpRequest current = jumpRequests.get(i); if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "PENDING_APPROVAL".equals(current.status())) { return jumpRequests.set(i, new ControlledJumpRequest(current.id(), current.ticketId(), current.applicantIamUserId(), current.sourceNode(), current.targetNode(), current.reason(), decision, current.createdAt(), approver, reason, at, current.approvalEngineInstanceId(), current.sourceTicketVersion(), current.sourceWorkflowVersion(), current.executorIamUserId(), current.executionStartedAt(), current.executedAt(), current.executedFromNode(), current.executedToNode(), current.executionFailureReason(), current.approvalPolicy())) != null; } } return false; }
    @Override public void appendApprovalDecision(cn.servicehub.workflow.domain.ApprovalDecisionRecord decision) { approvalDecisions.add(decision); }
    @Override public List<cn.servicehub.workflow.domain.ApprovalDecisionRecord> findApprovalDecisions(String ticketId, String requestId) { return approvalDecisions.stream().filter(value -> value.approvalRequestId().equals(requestId)).sorted(Comparator.comparing(cn.servicehub.workflow.domain.ApprovalDecisionRecord::decidedAt)).toList(); }
    @Override public boolean claimJumpExecution(String ticketId, String requestId, long sourceTicketVersion, long sourceWorkflowVersion, String executor, Instant startedAt) { for (int i = 0; i < jumpRequests.size(); i++) { ControlledJumpRequest current = jumpRequests.get(i); if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "APPROVED".equals(current.status()) && current.sourceTicketVersion() != null && current.sourceTicketVersion() == sourceTicketVersion && current.sourceWorkflowVersion() != null && current.sourceWorkflowVersion() == sourceWorkflowVersion) { return jumpRequests.set(i, new ControlledJumpRequest(current.id(), current.ticketId(), current.applicantIamUserId(), current.sourceNode(), current.targetNode(), current.reason(), "EXECUTING", current.createdAt(), current.approverIamUserId(), current.decisionReason(), current.decidedAt(), current.approvalEngineInstanceId(), current.sourceTicketVersion(), current.sourceWorkflowVersion(), executor, startedAt, null, null, null, null, current.approvalPolicy())) != null; } } return false; }
    @Override public boolean completeJumpExecution(String ticketId, String requestId, String from, String to, Instant executedAt) { for (int i = 0; i < jumpRequests.size(); i++) { ControlledJumpRequest current = jumpRequests.get(i); if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "EXECUTING".equals(current.status())) { return jumpRequests.set(i, new ControlledJumpRequest(current.id(), current.ticketId(), current.applicantIamUserId(), current.sourceNode(), current.targetNode(), current.reason(), "EXECUTED", current.createdAt(), current.approverIamUserId(), current.decisionReason(), current.decidedAt(), current.approvalEngineInstanceId(), current.sourceTicketVersion(), current.sourceWorkflowVersion(), current.executorIamUserId(), current.executionStartedAt(), executedAt, from, to, null, current.approvalPolicy())) != null; } } return false; }
    @Override public boolean releaseJumpExecution(String ticketId, String requestId) { for (int i = 0; i < jumpRequests.size(); i++) { ControlledJumpRequest current = jumpRequests.get(i); if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "EXECUTING".equals(current.status())) { return jumpRequests.set(i, new ControlledJumpRequest(current.id(), current.ticketId(), current.applicantIamUserId(), current.sourceNode(), current.targetNode(), current.reason(), "APPROVED", current.createdAt(), current.approverIamUserId(), current.decisionReason(), current.decidedAt(), current.approvalEngineInstanceId(), current.sourceTicketVersion(), current.sourceWorkflowVersion(), null, null, null, null, null, "ENGINE_EXECUTION_FAILED", current.approvalPolicy())) != null; } } return false; }
    @Override public void appendEvent(String ticketId, String action, String actorIamUserId, String requestId, java.util.Map<String, String> attributes, Instant occurredAt) { events.add(new cn.servicehub.workflow.domain.WorkflowEvent(eventSequence.incrementAndGet(), ticketId, action, actorIamUserId, requestId, attributes, occurredAt)); }
    @Override public List<cn.servicehub.workflow.domain.WorkflowEvent> findEvents(String ticketId) { return events.stream().filter(event -> event.ticketId().equals(ticketId)).sorted(Comparator.comparing(cn.servicehub.workflow.domain.WorkflowEvent::occurredAt)).toList(); }
}
