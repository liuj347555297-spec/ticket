package cn.servicehub.workflow.domain;

import java.time.Instant;

public record WorkflowTask(String id, String ticketId, String engineTaskId, String nodeKey,
                           WorkflowTaskStatus status, String candidateRole, String candidateIamUserId,
                           String assigneeIamUserId, CollaborationRole collaborationRole,
                           long version, Instant createdAt, Instant updatedAt) {
}
