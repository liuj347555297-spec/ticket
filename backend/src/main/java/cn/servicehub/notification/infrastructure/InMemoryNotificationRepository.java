package cn.servicehub.notification.infrastructure;

import cn.servicehub.notification.domain.Notification;
import cn.servicehub.notification.domain.NotificationRepository;
import cn.servicehub.notification.domain.NotificationDelivery;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryNotificationRepository implements NotificationRepository {
    private final ConcurrentHashMap<String, Notification> notifications = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> deduplication = new ConcurrentHashMap<>();
    @Override public boolean save(Notification notification) { return deduplication.putIfAbsent(notification.deduplicationKey(), notification.id()) == null && notifications.putIfAbsent(notification.id(), notification) == null; }
    @Override public List<Notification> findByRecipient(String user, String state, String category, int offset, int limit) { return notifications.values().stream().filter(n -> n.recipientIamUserId().equals(user)).filter(n -> state == null || ("READ".equals(state) == n.read())).filter(n -> category == null || n.category().equals(category)).sorted(Comparator.comparing(Notification::createdAt).reversed()).skip(offset).limit(limit).toList(); }
    @Override public long countByRecipient(String user, String state, String category) { return findByRecipient(user, state, category, 0, Integer.MAX_VALUE).size(); }
    @Override public java.util.Optional<Notification> markRead(String id, String recipient, long expected, Instant at) {
        Notification current = notifications.get(id);
        if (current == null || !current.recipientIamUserId().equals(recipient)) return java.util.Optional.empty();
        if (current.read()) return java.util.Optional.of(current);
        if (current.version() != expected) throw new cn.servicehub.workflow.application.WorkflowConflictException();
        Notification changed = new Notification(current.id(), current.recipientIamUserId(), current.category(), current.title(), current.body(), current.ticketId(), current.payload(), current.deduplicationKey(), at, current.createdAt(), current.version() + 1);
        notifications.replace(id, current, changed); return java.util.Optional.of(notifications.get(id));
    }
    @Override public java.util.Optional<List<NotificationDelivery>> findDeliveries(String id, String user, int offset, int limit) {
        Notification n = notifications.get(id); if (n == null || !n.recipientIamUserId().equals(user)) return java.util.Optional.empty();
        List<NotificationDelivery> rows = new java.util.ArrayList<>();
        rows.add(new NotificationDelivery("NDL-" + id.substring(4), cn.servicehub.notification.domain.MessageChannel.IN_APP, "DELIVERED", 1, n.createdAt(), null, null, n.createdAt(), n.createdAt()));
        if ("WPS_IM".equals(n.payload().get("preferredChannel"))) rows.add(new NotificationDelivery("NDL-WPS-" + id.substring(4), cn.servicehub.notification.domain.MessageChannel.WPS_IM, "SUPPRESSED", 0, n.createdAt(), null, "CHANNEL_DISABLED", n.createdAt(), null));
        return java.util.Optional.of(rows.stream().skip(offset).limit(limit).toList());
    }
    @Override public long countDeliveries(String id, String user) { return findDeliveries(id, user, 0, 100).map(List::size).orElse(0); }
}
