package cn.servicehub.notification.web;
import cn.servicehub.notification.application.NotificationDeliveryPage;
import cn.servicehub.notification.domain.NotificationDelivery;
import java.time.Instant;
import java.util.List;
record NotificationDeliveryPageResponse(List<Item> items, int page, int pageSize, long total) {
    static NotificationDeliveryPageResponse from(NotificationDeliveryPage page) { return new NotificationDeliveryPageResponse(page.items().stream().map(Item::from).toList(), page.page(), page.pageSize(), page.total()); }
    record Item(String id, String channel, String state, int attemptCount, Instant lastAttemptAt, Instant nextRetryAt, String terminalReasonCode, Instant createdAt, Instant deliveredAt) { static Item from(NotificationDelivery d) { return new Item(d.id(), d.channel().name(), d.state(), d.attemptCount(), d.lastAttemptAt(), d.nextRetryAt(), d.terminalReasonCode(), d.createdAt(), d.deliveredAt()); } }
}
