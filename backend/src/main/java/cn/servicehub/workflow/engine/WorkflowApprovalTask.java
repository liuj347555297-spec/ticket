package cn.servicehub.workflow.engine;

import java.time.Instant;

/**
 * Minimal Flowable task projection used by the approval inbox. The application never treats its
 * own approval row as proof that an engine task is actionable.
 */
public record WorkflowApprovalTask(String engineTaskId, String approvalRequestId, Instant createdAt) { }
