package cn.servicehub.workflow.domain;

import java.time.Instant;

public record WorkflowComment(String id, String ticketId, String authorIamUserId, String body, Instant createdAt) {
}
