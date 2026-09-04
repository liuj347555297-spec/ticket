package cn.servicehub.workflow.lifecycleapproval.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Separate persistence boundary: controlled-jump tables are never used for lifecycle approvals. */
public interface LifecycleActionApprovalRepository {
    void save(LifecycleActionApprovalRequest request);
    List<LifecycleActionApprovalRequest> findByTicketId(String ticketId);
    Optional<LifecycleActionApprovalRequest> find(String ticketId, String requestId);
    Optional<LifecycleActionApprovalRequest> findById(String requestId);
    boolean finalizeDecision(String ticketId, String requestId, String decision, String approverIamUserId, String reason, Instant at);
    boolean markExecuted(String ticketId, String requestId, long sourceTicketVersion, long sourceWorkflowVersion, Instant at);
    boolean markStale(String ticketId, String requestId, Instant at);
    List<LifecycleActionApprovalRequest> findPendingDueBefore(Instant now, int limit);
    boolean claimExpiration(String ticketId, String requestId, Instant at);
    boolean markExpired(String ticketId, String requestId, Instant at);
    void appendTimeoutEvent(String id, LifecycleActionApprovalRequest request, Instant at);
}
