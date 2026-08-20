package cn.servicehub.notification.web;

import cn.servicehub.notification.domain.Notification;
import java.time.Instant;
import java.util.Map;

public record NotificationResponse(String id, String category, String title, String body, String sourceType, String sourceDisplayReference, String readState, Instant readAt, Instant createdAt, long version) {
    static NotificationResponse from(Notification n) { return new NotificationResponse(n.id(), n.category(), n.title(), n.body(), "TICKET", n.ticketId(), n.read() ? "READ" : "UNREAD", n.readAt(), n.createdAt(), n.version()); }
}
