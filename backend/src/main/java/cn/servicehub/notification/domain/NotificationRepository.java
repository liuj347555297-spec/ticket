package cn.servicehub.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port; implementations create notification, in-app delivery and outbox atomically. */
public interface NotificationRepository {
    /** @return false when an event is already represented in its server-calculated aggregation window. */
    boolean save(Notification notification);
    List<Notification> findByRecipient(String iamUserId, String readState, String category, int offset, int limit);
    long countByRecipient(String iamUserId, String readState, String category);
    Optional<Notification> markRead(String notificationId, String recipientIamUserId, long expectedVersion, Instant readAt);
    Optional<List<NotificationDelivery>> findDeliveries(String notificationId, String recipientIamUserId, int offset, int limit);
    long countDeliveries(String notificationId, String recipientIamUserId);
}
