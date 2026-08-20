package cn.servicehub.notification.domain;

import java.time.Instant;
import java.util.Map;

/** Immutable user-facing notification. Recipient is always an IAM projection ID. */
public record Notification(
    String id,
    String recipientIamUserId,
    String category,
    String title,
    String body,
    String ticketId,
    Map<String, String> payload,
    String deduplicationKey,
    Instant readAt,
    Instant createdAt,
    long version) {
    public Notification { payload = payload == null ? Map.of() : Map.copyOf(payload); }
    public boolean read() { return readAt != null; }
}
