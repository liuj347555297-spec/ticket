package cn.servicehub.workflow.domain;

import java.time.Instant;

/** An application is deliberately not an engine command. Approval and a separate execution path are required. */
public record ControlledJumpRequest(String id, String ticketId, String applicantIamUserId, String sourceNode,
                                    String targetNode, String reason, String status, Instant createdAt,
                                    String approverIamUserId, String decisionReason, Instant decidedAt, String approvalEngineInstanceId,
                                    Long sourceTicketVersion, Long sourceWorkflowVersion,
                                    String executorIamUserId, Instant executionStartedAt, Instant executedAt,
                                    String executedFromNode, String executedToNode, String executionFailureReason,
                                    ApprovalPolicySnapshot approvalPolicy) {
    public ControlledJumpRequest(String id, String ticketId, String applicantIamUserId, String sourceNode,
                                 String targetNode, String reason, String status, Instant createdAt) {
        this(id, ticketId, applicantIamUserId, sourceNode, targetNode, reason, status, createdAt, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }

    public ControlledJumpRequest(String id, String ticketId, String applicantIamUserId, String sourceNode,
                                 String targetNode, String reason, String status, Instant createdAt,
                                 String approverIamUserId, String decisionReason, Instant decidedAt, String approvalEngineInstanceId,
                                 Long sourceTicketVersion, Long sourceWorkflowVersion) {
        this(id, ticketId, applicantIamUserId, sourceNode, targetNode, reason, status, createdAt,
            approverIamUserId, decisionReason, decidedAt, approvalEngineInstanceId, sourceTicketVersion, sourceWorkflowVersion,
            null, null, null, null, null, null, null);
    }
}
