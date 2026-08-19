package cn.servicehub.audit;

import java.time.Instant;
import java.util.Map;

/** Do not put credentials, session identifiers, tokens or attachment contents in attributes. */
public record AuditEvent(Instant occurredAt, String requestId, String actorIamUserId, String action,
                         String resourceType, String resourceId, Map<String, String> attributes) {
    public AuditEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
