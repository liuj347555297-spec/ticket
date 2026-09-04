package cn.servicehub.notification.infrastructure;

import cn.servicehub.notification.application.ExternalMessageDeliveryException;
import cn.servicehub.notification.application.MessageChannelPort;
import cn.servicehub.notification.application.NotificationDispatchProperties;
import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.Notification;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL-only outbox dispatcher. It claims rows using a short database transaction, then calls a
 * provider outside that transaction. The durable IN_APP delivery is already created with the
 * notification, so an unavailable external channel can never hide a user notification.
 */
@Component
@Profile("mysql")
public class NotificationOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxDispatcher.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Map<MessageChannel, MessageChannelPort> channels;
    private final NotificationDispatchProperties properties;
    private final ObjectMapper json;

    public NotificationOutboxDispatcher(JdbcTemplate jdbc, TransactionTemplate transactions, List<MessageChannelPort> adapters,
                                        NotificationDispatchProperties properties, ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.json = json;
        Map<MessageChannel, MessageChannelPort> configured = new LinkedHashMap<>();
        for (MessageChannelPort adapter : adapters) configured.put(adapter.channel(), adapter);
        this.channels = Map.copyOf(configured);
    }

    @Scheduled(fixedDelayString = "${servicehub.notification.dispatch.fixed-delay-ms:10000}")
    public void dispatchScheduled() {
        Instant now = Instant.now();
        recoverExpiredClaims(now);
        for (DispatchItem item : claimDue(now)) dispatch(item);
    }

    private void recoverExpiredClaims(Instant now) {
        transactions.executeWithoutResult(status -> {
            Instant expiredBefore = now.minusMillis(properties.deliveringLeaseMs());
            List<String> outboxIds = jdbc.query("""
                SELECT id FROM message_outbox
                 WHERE status='DELIVERING' AND available_at < ?
                 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> rs.getString(1), Timestamp.from(expiredBefore));
            for (String outboxId : outboxIds) {
                jdbc.update("UPDATE message_outbox SET status='RETRY_SCHEDULED', available_at=?, last_error='DELIVERY_LEASE_EXPIRED' WHERE id=? AND status='DELIVERING'", Timestamp.from(now), outboxId);
                jdbc.update("""
                    UPDATE notification_delivery d JOIN message_outbox o ON o.aggregate_id=d.notification_id
                       SET d.status='RETRY_SCHEDULED', d.next_attempt_at=?, d.last_error_code='DELIVERY_LEASE_EXPIRED', d.updated_at=?
                     WHERE o.id=? AND d.channel='WPS_IM' AND d.status='DELIVERING'
                    """, Timestamp.from(now), Timestamp.from(now), outboxId);
            }
        });
    }

    private List<DispatchItem> claimDue(Instant now) {
        return transactions.execute(status -> {
            List<DispatchItem> items = jdbc.query("""
                SELECT o.id AS outbox_id, o.aggregate_id, o.attempt_count AS outbox_attempt_count,
                       d.id AS delivery_id, d.attempt_count AS delivery_attempt_count,
                       n.id, n.recipient_iam_user_id, n.category, n.title, n.body, n.ticket_id, n.payload,
                       n.deduplication_key, n.read_at, n.created_at, n.version
                  FROM message_outbox o
                  JOIN notification n ON n.id=o.aggregate_id
                  JOIN notification_delivery d ON d.notification_id=n.id AND d.channel='WPS_IM'
                 WHERE o.status IN ('PENDING', 'RETRY_SCHEDULED') AND o.available_at <= ?
                   AND d.status IN ('PENDING', 'RETRY_SCHEDULED')
                 ORDER BY o.created_at, o.id
                 LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new DispatchItem(rs.getString("outbox_id"), rs.getString("delivery_id"),
                rs.getInt("delivery_attempt_count") + 1, notification(rs)), Timestamp.from(now), properties.batchSize());
            for (DispatchItem item : items) {
                jdbc.update("UPDATE message_outbox SET status='DELIVERING', attempt_count=attempt_count+1, available_at=?, last_error=NULL WHERE id=? AND status IN ('PENDING', 'RETRY_SCHEDULED')", Timestamp.from(now), item.outboxId());
                jdbc.update("UPDATE notification_delivery SET status='DELIVERING', attempt_count=attempt_count+1, next_attempt_at=NULL, last_error_code=NULL, last_error_message=NULL, updated_at=? WHERE id=? AND status IN ('PENDING', 'RETRY_SCHEDULED')", Timestamp.from(now), item.deliveryId());
            }
            return items;
        });
    }

    private void dispatch(DispatchItem item) {
        try {
            // External routes are created only for WPS_IM. No browser input controls this selection.
            MessageChannelPort adapter = channels.get(MessageChannel.WPS_IM);
            if (adapter == null || !adapter.enabled()) throw new ExternalMessageDeliveryException("WPS_IM_NOT_CONFIGURED", false);
            adapter.deliver(item.notification());
            // A provider adapter must return normally only after a provider-acknowledged send.
            delivered(item);
        } catch (ExternalMessageDeliveryException failure) {
            fail(item, failure.safeCode(), failure.retryable());
        } catch (RuntimeException unexpected) {
            // Never retain a provider response, endpoint, credential, recipient or stack trace in durable state.
            log.warn("Notification dispatch failed safely: outboxId={}, code=UNEXPECTED_DISPATCH_FAILURE", item.outboxId());
            fail(item, "UNEXPECTED_DISPATCH_FAILURE", true);
        }
    }

    private void delivered(DispatchItem item) {
        Instant now = Instant.now();
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE message_outbox SET status='DELIVERED', processed_at=?, last_error=NULL WHERE id=? AND status='DELIVERING'", Timestamp.from(now), item.outboxId());
            jdbc.update("UPDATE notification_delivery SET status='DELIVERED', delivered_at=?, updated_at=?, next_attempt_at=NULL, last_error_code=NULL, last_error_message=NULL WHERE id=? AND status='DELIVERING'", Timestamp.from(now), Timestamp.from(now), item.deliveryId());
        });
    }

    private void fail(DispatchItem item, String safeCode, boolean retryable) {
        Instant now = Instant.now();
        boolean retry = retryable && item.attemptNumber() < properties.maxAttempts();
        Instant next = retry ? now.plusMillis(backoffMillis(item.attemptNumber())) : null;
        String deliveryStatus = retry ? "RETRY_SCHEDULED" : "FAILED_FINAL";
        transactions.executeWithoutResult(status -> {
            jdbc.update("UPDATE message_outbox SET status=?, available_at=?, processed_at=?, last_error=? WHERE id=? AND status='DELIVERING'",
                deliveryStatus, Timestamp.from(retry ? next : now), retry ? null : Timestamp.from(now), safeCode, item.outboxId());
            jdbc.update("UPDATE notification_delivery SET status=?, next_attempt_at=?, last_error_code=?, last_error_message=NULL, updated_at=? WHERE id=? AND status='DELIVERING'",
                deliveryStatus, retry ? Timestamp.from(next) : null, safeCode, Timestamp.from(now), item.deliveryId());
        });
    }

    private long backoffMillis(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        return Math.min(properties.retryBaseDelayMs() * multiplier, 3_600_000L);
    }

    private Notification notification(java.sql.ResultSet rs) throws java.sql.SQLException {
        try {
            Map<String, String> payload = json.readValue(rs.getString("payload"), new TypeReference<Map<String, String>>() { });
            Timestamp readAt = rs.getTimestamp("read_at");
            return new Notification(rs.getString("id"), rs.getString("recipient_iam_user_id"), rs.getString("category"),
                rs.getString("title"), rs.getString("body"), rs.getString("ticket_id"), payload, rs.getString("deduplication_key"),
                readAt == null ? null : readAt.toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid durable notification outbox payload", e);
        }
    }

    private record DispatchItem(String outboxId, String deliveryId, int attemptNumber, Notification notification) { }
}
