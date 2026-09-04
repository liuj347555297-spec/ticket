package cn.servicehub.workflow.application;

import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRequest;
import java.time.Instant;

/** Browser-safe lifecycle approval projection; frozen candidate IAM IDs remain server-side only. */
public record LifecycleActionApprovalSummary(String id, WorkflowAction action, String applicantIamUserId,
                                             String reason, String targetIamUserId, String status, String processKey, int processVersion,
                                             String policyId, long policyVersion, String decisionMode, int requiredApprovalCount,
                                             String timeoutPolicyVersion, String escalationPolicyVersion, Instant dueAt, int candidateApprovalCount, String approverIamUserId,
                                             String decisionReason, Instant decidedAt, Instant executedAt,
                                             Instant createdAt) {
    public static LifecycleActionApprovalSummary from(LifecycleActionApprovalRequest request) {
        return new LifecycleActionApprovalSummary(request.id(), request.action(), request.applicantIamUserId(),
            request.reason(), request.targetIamUserId(), request.status(), request.processKey(), request.processVersion(),
            request.policyId(), request.policyVersion(), request.decisionMode(), request.requiredApprovalCount(), request.timeoutPolicyVersion(), request.escalationPolicyVersion(), request.dueAt(), request.candidateIamUserIds().size(), request.approverIamUserId(), request.decisionReason(),
            request.decidedAt(), request.executedAt(), request.createdAt());
    }
}
