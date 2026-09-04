package cn.servicehub.workflow.team;

import java.time.Instant;
import java.util.Set;

/** Append-only evidence for one concrete workflow task routing decision. */
public record WorkflowQueueRoutingSnapshot(String id, String ticketId, String workflowTaskId, String nodeKey,
                                           String queueCode, NodeAssignmentEvidence assignment,
                                           long queueVersion, String queueScopeDigest,
                                           Set<String> candidateIamUserIds, String ticketContextDigest,
                                           Instant capturedAt) {
    public WorkflowQueueRoutingSnapshot { candidateIamUserIds = candidateIamUserIds == null ? Set.of() : Set.copyOf(candidateIamUserIds); }
    public record NodeAssignmentEvidence(String mode, long policyVersion) { }
}
