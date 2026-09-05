package cn.servicehub.workflow.infrastructure;

import cn.servicehub.workflow.domain.ControlledJumpRequest;
import cn.servicehub.workflow.domain.HandoverRequest;
import cn.servicehub.workflow.domain.CoHandlerRequest;
import cn.servicehub.workflow.domain.CollaborationRole;
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
    private final CopyOnWriteArrayList<cn.servicehub.workflow.domain.TicketDelegation> delegations = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowParticipant>> participants = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<WorkflowComment> comments = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<HandoverRequest> handoverRequests = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<CoHandlerRequest> coHandlerRequests = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ControlledJumpRequest> jumpRequests = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<cn.servicehub.workflow.domain.ApprovalDecisionRecord> approvalDecisions = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<cn.servicehub.workflow.domain.WorkflowEvent> events = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong eventSequence = new java.util.concurrent.atomic.AtomicLong();

    @Override public void create(WorkflowInstance instance, WorkflowTask initialTask) {
        if (instances.putIfAbsent(instance.ticketId(), instance) != null) throw new IllegalStateException("Workflow already exists");
        tasks.put(initialTask.id(), initialTask);
    }
    @Override public Optional<WorkflowInstance> findInstance(String ticketId) { return Optional.ofNullable(instances.get(ticketId)); }
    @Override public Optional<WorkflowInstance> findInstanceForUpdate(String ticketId) { return findInstance(ticketId); }
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
    @Override public synchronized boolean claimOpenTask(String id,long version,String queue,String user,java.time.Instant at){WorkflowTask t=tasks.get(id);if(t==null||t.version()!=version||t.status()!=WorkflowTaskStatus.OPEN||t.assigneeIamUserId()!=null||!java.util.Objects.equals(queue,t.queueCode()))return false;tasks.put(id,new WorkflowTask(t.id(),t.ticketId(),t.engineTaskId(),t.nodeKey(),WorkflowTaskStatus.CLAIMED,t.candidateRole(),user,user,CollaborationRole.PRIMARY,t.version()+1,t.createdAt(),at,t.queueCode()));return true;}
    @Override public long countOpenTasksByQueue(String q){return tasks.values().stream().filter(t->q.equals(t.queueCode())&&t.status()==WorkflowTaskStatus.OPEN&&t.assigneeIamUserId()==null).count();}
    @Override public long countClaimedTasksByQueue(String q){return tasks.values().stream().filter(t->q.equals(t.queueCode())&&t.status()==WorkflowTaskStatus.CLAIMED).count();}
    @Override public synchronized long migrateOpenTasks(String source,String target){long n=0;for(WorkflowTask t:java.util.List.copyOf(tasks.values()))if(source.equals(t.queueCode())&&t.status()==WorkflowTaskStatus.OPEN&&t.assigneeIamUserId()==null){tasks.put(t.id(),new WorkflowTask(t.id(),t.ticketId(),t.engineTaskId(),t.nodeKey(),t.status(),t.candidateRole(),t.candidateIamUserId(),t.assigneeIamUserId(),t.collaborationRole(),t.version()+1,t.createdAt(),java.time.Instant.now(),target));n++;}return n;}
    @Override public List<WorkflowTask> findActiveTasksByQueue(String q){return tasks.values().stream().filter(t->q.equals(t.queueCode())&&(t.status()==WorkflowTaskStatus.OPEN||t.status()==WorkflowTaskStatus.CLAIMED)).sorted(java.util.Comparator.comparing(WorkflowTask::createdAt)).toList();}
    @Override public synchronized boolean migrateOpenTask(String id,long v,String source,String target,String ticketId,long ticketVersion,long workflowVersion,Instant at){WorkflowTask t=tasks.get(id);WorkflowInstance wi=instances.get(ticketId);if(t==null||!t.ticketId().equals(ticketId)||wi==null||wi.version()!=workflowVersion||t.version()!=v||!source.equals(t.queueCode())||t.status()!=WorkflowTaskStatus.OPEN||t.assigneeIamUserId()!=null)return false;tasks.put(id,new WorkflowTask(t.id(),t.ticketId(),t.engineTaskId(),t.nodeKey(),t.status(),t.candidateRole(),t.candidateIamUserId(),null,t.collaborationRole(),v+1,t.createdAt(),at,target));return true;}
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
    @Override public void addDelegation(cn.servicehub.workflow.domain.TicketDelegation delegation) { delegations.add(delegation); }
    @Override public boolean hasActiveDelegation(String ticketId, String delegator, String delegate, Instant at) { return delegations.stream().anyMatch(value -> value.ticketId().equals(ticketId) && value.delegatorIamUserId().equals(delegator) && value.delegateIamUserId().equals(delegate) && !at.isBefore(value.effectiveFrom()) && at.isBefore(value.effectiveUntil())); }
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
    @Override public void addHandoverRequest(HandoverRequest request) { handoverRequests.add(request); }
    @Override public List<HandoverRequest> findHandoverRequests(String ticketId) { return handoverRequests.stream().filter(item -> item.ticketId().equals(ticketId)).sorted(Comparator.comparing(HandoverRequest::createdAt)).toList(); }
    @Override public Optional<HandoverRequest> findHandoverRequest(String ticketId, String requestId) { return handoverRequests.stream().filter(item -> item.ticketId().equals(ticketId) && item.id().equals(requestId)).findFirst(); }
    @Override public Optional<HandoverRequest> findHandoverRequestById(String requestId) { return handoverRequests.stream().filter(item -> item.id().equals(requestId)).findFirst(); }
    @Override public boolean finalizeHandoverRequest(String ticketId, String requestId, String decision, String reason, Instant decidedAt, long sourceTicketVersion, long sourceWorkflowVersion) {
        for (int i = 0; i < handoverRequests.size(); i++) {
            HandoverRequest current = handoverRequests.get(i);
            if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "PENDING_CONFIRMATION".equals(current.status())) {
                String status = current.sourceTicketVersion() == sourceTicketVersion && current.sourceWorkflowVersion() == sourceWorkflowVersion ? decision : "STALE";
                return handoverRequests.set(i, new HandoverRequest(current.id(), current.ticketId(), current.engineInstanceId(), current.processDefinitionId(),
                    current.processDefinitionVersion(), current.applicantIamUserId(), current.targetIamUserId(), current.reason(), status,
                    current.sourceTicketVersion(), current.sourceWorkflowVersion(), decidedAt, reason, current.createdAt())) != null;
            }
        }
        return false;
    }
    @Override public void addCoHandlerRequest(CoHandlerRequest request) { coHandlerRequests.add(request); }
    @Override public List<CoHandlerRequest> findCoHandlerRequests(String ticketId) { return coHandlerRequests.stream().filter(item -> item.ticketId().equals(ticketId)).sorted(Comparator.comparing(CoHandlerRequest::createdAt)).toList(); }
    @Override public Optional<CoHandlerRequest> findCoHandlerRequest(String ticketId, String requestId) { return coHandlerRequests.stream().filter(item -> item.ticketId().equals(ticketId) && item.id().equals(requestId)).findFirst(); }
    @Override public Optional<CoHandlerRequest> findCoHandlerRequestById(String requestId) { return coHandlerRequests.stream().filter(item -> item.id().equals(requestId)).findFirst(); }
    @Override public boolean finalizeCoHandlerRequest(String ticketId, String requestId, String decision, String reason, Instant decidedAt, long sourceTicketVersion, long sourceWorkflowVersion) {
        for (int i = 0; i < coHandlerRequests.size(); i++) {
            CoHandlerRequest current = coHandlerRequests.get(i);
            if (current.ticketId().equals(ticketId) && current.id().equals(requestId) && "PENDING_CONFIRMATION".equals(current.status())) {
                String status = current.sourceTicketVersion() == sourceTicketVersion && current.sourceWorkflowVersion() == sourceWorkflowVersion ? decision : "STALE";
                return coHandlerRequests.set(i, new CoHandlerRequest(current.id(), current.ticketId(), current.engineInstanceId(), current.processDefinitionId(), current.processDefinitionVersion(), current.applicantIamUserId(), current.targetIamUserId(), current.reason(), status, current.sourceTicketVersion(), current.sourceWorkflowVersion(), decidedAt, reason, current.createdAt())) != null;
            }
        }
        return false;
    }
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
