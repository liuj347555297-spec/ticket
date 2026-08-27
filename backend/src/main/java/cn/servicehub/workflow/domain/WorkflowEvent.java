package cn.servicehub.workflow.domain;

import java.time.Instant;
import java.util.Map;

/** Immutable platform workflow history; it supplements, but never replaces, the audit sink. */
public record WorkflowEvent(long id, String ticketId, String action, String actorIamUserId, String requestId,
                            Map<String, String> attributes, Instant occurredAt) {
    public WorkflowEvent { attributes = attributes == null ? Map.of() : Map.copyOf(attributes); }
}
