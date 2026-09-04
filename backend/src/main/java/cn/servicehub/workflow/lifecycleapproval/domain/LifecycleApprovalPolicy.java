package cn.servicehub.workflow.lifecycleapproval.domain;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.workflow.domain.WorkflowAction;
import java.time.Instant;
import java.util.Set;

/** A published policy is selected only on the server and is subsequently copied into each request. */
public record LifecycleApprovalPolicy(String id, String name, WorkflowAction action, String serviceCatalogItemId,
    TicketPriority priority, Set<String> candidateRoles, String decisionMode, int approvalThresholdPercent,
    int timeoutMinutes, String timeoutPolicyVersion, String escalationPolicyVersion, String status, long version,
    Instant createdAt, Instant updatedAt, Instant publishedAt) {
    public LifecycleApprovalPolicy {
        candidateRoles = candidateRoles == null ? Set.of() : Set.copyOf(candidateRoles);
        if (id == null || name == null || action == null || candidateRoles.isEmpty()
            || !Set.of("ANY_ONE", "ALL_OF", "QUORUM").contains(decisionMode)
            || approvalThresholdPercent < 1 || approvalThresholdPercent > 100 || timeoutMinutes < 1
            || timeoutPolicyVersion == null || escalationPolicyVersion == null || status == null) {
            throw new IllegalArgumentException("Lifecycle approval policy is invalid");
        }
    }
}
