package cn.servicehub.workflow.engine;

import java.time.Instant;

/** A minimal, caller-owned Flowable task reference. Domain projections are loaded and authorized separately. */
public record WorkflowInboxTask(String engineTaskId, String processKey, String requestId, String ticketId,
                                Instant createdAt) {
}
