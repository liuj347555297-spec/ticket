package cn.servicehub.workflow.domain;

import java.time.Instant;

/** An application is deliberately not an engine command. Approval and a separate execution path are required. */
public record ControlledJumpRequest(String id, String ticketId, String applicantIamUserId, String sourceNode,
                                    String targetNode, String reason, String status, Instant createdAt) {
}
