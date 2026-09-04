package cn.servicehub.workflow.domain;

import java.time.Instant;

/**
 * Immutable request to add a collaborator. The target is added only after completing the
 * assigned Flowable confirmation task; this projection is evidence, not an approval substitute.
 */
public record CoHandlerRequest(String id, String ticketId, String engineInstanceId,
                               String processDefinitionId, int processDefinitionVersion,
                               String applicantIamUserId, String targetIamUserId, String reason,
                               String status, long sourceTicketVersion, long sourceWorkflowVersion,
                               Instant decidedAt, String decisionReason, Instant createdAt) {
}
