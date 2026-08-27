package cn.servicehub.workflow.domain;

import java.time.Instant;

/** Immutable, append-only decision evidence; it never acts as a replacement for Flowable state. */
public record ApprovalDecisionRecord(String id, String approvalRequestId, String engineTaskId,
                                     String approverIamUserId, String decision, String reason, Instant decidedAt) {
}
