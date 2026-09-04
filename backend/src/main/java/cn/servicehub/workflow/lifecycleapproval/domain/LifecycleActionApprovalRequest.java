package cn.servicehub.workflow.lifecycleapproval.domain;

import cn.servicehub.workflow.domain.WorkflowAction;
import java.time.Instant;
import java.util.Set;

/** Immutable request projection for a high-risk lifecycle action. */
public record LifecycleActionApprovalRequest(String id, String ticketId, WorkflowAction action, String applicantIamUserId,
    String reason, String targetIamUserId, long sourceTicketVersion, long sourceWorkflowVersion, String approvalEngineInstanceId,
    String processKey, String processDefinitionId, int processVersion, String policyId, long policyVersion,
    String decisionMode, int requiredApprovalCount, String timeoutPolicyVersion, String escalationPolicyVersion,
    Instant dueAt, Set<String> candidateRoles, Set<String> candidateIamUserIds, String status, String approverIamUserId,
    String decisionReason, Instant decidedAt, Instant executedAt, Instant createdAt) { }
