package cn.servicehub.workflow.domain;

import java.time.Instant;

/** Immutable, two-party handover request. The assignee changes only after its Flowable task is completed. */
public record HandoverRequest(String id, String ticketId, String engineInstanceId, String processDefinitionId,
                              int processDefinitionVersion, String applicantIamUserId, String targetIamUserId,
                              String reason, String status, long sourceTicketVersion, long sourceWorkflowVersion,
                              Instant decidedAt, String decisionReason, Instant createdAt) {
}
