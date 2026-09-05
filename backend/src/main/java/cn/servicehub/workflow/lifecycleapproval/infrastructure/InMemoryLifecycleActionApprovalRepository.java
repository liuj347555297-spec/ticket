package cn.servicehub.workflow.lifecycleapproval.infrastructure;

import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRepository;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryLifecycleActionApprovalRepository implements LifecycleActionApprovalRepository {
    private final CopyOnWriteArrayList<LifecycleActionApprovalRequest> requests = new CopyOnWriteArrayList<>();
    @Override public synchronized void save(LifecycleActionApprovalRequest request) {
        boolean duplicate = requests.stream().anyMatch(current -> current.ticketId().equals(request.ticketId())
            && current.action() == request.action()
            && current.sourceTicketVersion() == request.sourceTicketVersion()
            && current.sourceWorkflowVersion() == request.sourceWorkflowVersion()
            && java.util.Set.of("PENDING_APPROVAL", "EXPIRING").contains(current.status()));
        if (duplicate) throw new cn.servicehub.workflow.application.WorkflowConflictException();
        requests.add(request);
    }
    @Override public List<LifecycleActionApprovalRequest> findByTicketId(String ticketId) { return requests.stream().filter(item -> item.ticketId().equals(ticketId)).sorted(Comparator.comparing(LifecycleActionApprovalRequest::createdAt)).toList(); }
    @Override public Optional<LifecycleActionApprovalRequest> find(String ticketId, String requestId) { return requests.stream().filter(item -> item.ticketId().equals(ticketId) && item.id().equals(requestId)).findFirst(); }
    @Override public Optional<LifecycleActionApprovalRequest> findById(String requestId) { return requests.stream().filter(item -> item.id().equals(requestId)).findFirst(); }
    @Override public boolean finalizeDecision(String ticketId, String requestId, String decision, String approver, String reason, Instant at) { return replace(ticketId, requestId, current -> "PENDING_APPROVAL".equals(current.status()) ? copy(current, decision, approver, reason, at, current.executedAt()) : null); }
    @Override public boolean markExecuted(String ticketId, String requestId, long ticketVersion, long workflowVersion, Instant at) { return replace(ticketId, requestId, current -> "APPROVED".equals(current.status()) && current.sourceTicketVersion() == ticketVersion && current.sourceWorkflowVersion() == workflowVersion ? copy(current, "EXECUTED", current.approverIamUserId(), current.decisionReason(), current.decidedAt(), at) : null); }
    @Override public boolean markStale(String ticketId, String requestId, Instant at) { return replace(ticketId, requestId, current -> "APPROVED".equals(current.status()) ? copy(current, "STALE", current.approverIamUserId(), current.decisionReason(), current.decidedAt(), at) : null); }
    @Override public List<LifecycleActionApprovalRequest> findPendingDueBefore(Instant now,int limit) { return requests.stream().filter(r -> "PENDING_APPROVAL".equals(r.status()) && !r.dueAt().isAfter(now)).limit(limit).toList(); }
    @Override public boolean claimExpiration(String ticketId,String requestId,Instant at) { return replace(ticketId,requestId,current -> "PENDING_APPROVAL".equals(current.status()) && !current.dueAt().isAfter(at) ? copy(current,"EXPIRING",current.approverIamUserId(),current.decisionReason(),current.decidedAt(),current.executedAt()):null); }
    @Override public boolean markExpired(String ticketId,String requestId,Instant at) { return replace(ticketId,requestId,current -> "EXPIRING".equals(current.status()) ? copy(current,"EXPIRED",current.approverIamUserId(),current.decisionReason(),current.decidedAt(),at):null); }
    @Override public void appendTimeoutEvent(String id,LifecycleActionApprovalRequest request,Instant at) { /* immutable request status is sufficient for in-memory test projection */ }
    private boolean replace(String ticketId, String requestId, java.util.function.Function<LifecycleActionApprovalRequest, LifecycleActionApprovalRequest> change) { for (int i = 0; i < requests.size(); i++) { LifecycleActionApprovalRequest current = requests.get(i); if (current.ticketId().equals(ticketId) && current.id().equals(requestId)) { LifecycleActionApprovalRequest next = change.apply(current); return next != null && requests.set(i, next) != null; } } return false; }
    private LifecycleActionApprovalRequest copy(LifecycleActionApprovalRequest r, String status, String approver, String reason, Instant decidedAt, Instant executedAt) { return new LifecycleActionApprovalRequest(r.id(), r.ticketId(), r.action(), r.applicantIamUserId(), r.reason(), r.targetIamUserId(), r.sourceTicketVersion(), r.sourceWorkflowVersion(), r.approvalEngineInstanceId(), r.processKey(), r.processDefinitionId(), r.processVersion(), r.policyId(),r.policyVersion(),r.decisionMode(),r.requiredApprovalCount(),r.timeoutPolicyVersion(),r.escalationPolicyVersion(),r.dueAt(),r.candidateRoles(), r.candidateIamUserIds(), status, approver, reason, decidedAt, executedAt, r.createdAt()); }
}
