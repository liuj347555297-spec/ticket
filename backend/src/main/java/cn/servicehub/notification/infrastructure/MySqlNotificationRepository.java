package cn.servicehub.notification.infrastructure;

import cn.servicehub.notification.domain.Notification;
import cn.servicehub.notification.domain.NotificationRepository;
import cn.servicehub.notification.domain.NotificationDelivery;
import cn.servicehub.notification.domain.MessageChannel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("mysql")
public class MySqlNotificationRepository implements NotificationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlNotificationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override @Transactional public boolean save(Notification n) {
        try {
            jdbc.update("INSERT INTO notification (id, recipient_iam_user_id, category, title, body, ticket_id, payload, routing_snapshot, deduplication_key, read_at, version, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", n.id(), n.recipientIamUserId(), n.category(), n.title(), n.body(), n.ticketId(), json.writeValueAsString(n.payload()), json.writeValueAsString(Map.of("targetPath", "/tickets/" + n.ticketId(), "routeRuleId", n.payload().getOrDefault("routeRuleId", "DEFAULT-IN_APP"))), n.deduplicationKey(), ts(n.readAt()), n.version(), ts(n.createdAt()));
            jdbc.update("INSERT INTO notification_delivery (id, notification_id, channel, provider_channel_code, route_rule_id, status, attempt_count, created_at, updated_at, delivered_at) VALUES (?, ?, 'IN_APP', NULL, ?, 'DELIVERED', 1, ?, ?, ?)", "NDL-" + UUID.randomUUID(), n.id(), n.payload().getOrDefault("routeRuleId", "DEFAULT-IN_APP"), ts(n.createdAt()), ts(n.createdAt()), ts(n.createdAt()));
            // The in-app projection is the mandatory fallback.  An external WPS route receives a
            // separate PENDING delivery and durable outbox record; the browser never invokes it.
            if ("WPS_IM".equals(n.payload().get("preferredChannel"))) {
                jdbc.update("INSERT INTO notification_delivery (id, notification_id, channel, provider_channel_code, route_rule_id, status, attempt_count, created_at, updated_at) VALUES (?, ?, 'WPS_IM', ?, ?, 'PENDING', 0, ?, ?)", "NDL-" + UUID.randomUUID(), n.id(), n.payload().get("providerChannelCode"), n.payload().get("routeRuleId"), ts(n.createdAt()), ts(n.createdAt()));
                jdbc.update("INSERT INTO message_outbox (id, aggregate_type, aggregate_id, event_type, payload, status, attempt_count, available_at, processed_at, created_at) VALUES (?, 'notification', ?, 'NOTIFICATION_WPS_IM_REQUESTED', ?, 'PENDING', 0, ?, NULL, ?)", "OUT-" + UUID.randomUUID(), n.id(), json.writeValueAsString(Map.of("notificationId", n.id(), "channel", "WPS_IM")), ts(n.createdAt()), ts(n.createdAt()));
            }
            return true;
        } catch (org.springframework.dao.DuplicateKeyException duplicate) { return false;
        } catch (Exception e) { throw new IllegalStateException("Notification outbox cannot be persisted", e); }
    }
    @Override public List<Notification> findByRecipient(String user, String state, String category, int offset, int limit) { String where = "recipient_iam_user_id=?" + ("READ".equals(state) ? " AND read_at IS NOT NULL" : "UNREAD".equals(state) ? " AND read_at IS NULL" : "") + (category == null ? "" : " AND category=?"); Object[] args = category == null ? new Object[]{user, limit, offset} : new Object[]{user, category, limit, offset}; return jdbc.query("SELECT * FROM notification WHERE " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", (rs, row) -> row(rs), args); }
    @Override public long countByRecipient(String user, String state, String category) { String where = "recipient_iam_user_id=?" + ("READ".equals(state) ? " AND read_at IS NOT NULL" : "UNREAD".equals(state) ? " AND read_at IS NULL" : "") + (category == null ? "" : " AND category=?"); Object[] args = category == null ? new Object[]{user} : new Object[]{user, category}; return jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE " + where, Long.class, args); }
    @Override public List<String> findUnreadTicketIds(String user) { return jdbc.queryForList("SELECT DISTINCT ticket_id FROM notification WHERE recipient_iam_user_id=? AND read_at IS NULL AND ticket_id IS NOT NULL", String.class, user); }
    @Override public java.util.Optional<Notification> markRead(String id, String user, long version, Instant readAt) { List<Notification> current = jdbc.query("SELECT * FROM notification WHERE id=? AND recipient_iam_user_id=?", (rs,row)->row(rs), id,user); if (current.isEmpty()) return java.util.Optional.empty(); Notification n=current.getFirst(); if (n.read()) return java.util.Optional.of(n); if (jdbc.update("UPDATE notification SET read_at=?, version=version+1 WHERE id=? AND recipient_iam_user_id=? AND version=? AND read_at IS NULL", ts(readAt),id,user,version)!=1) throw new cn.servicehub.workflow.application.WorkflowConflictException(); return java.util.Optional.of(new Notification(n.id(),n.recipientIamUserId(),n.category(),n.title(),n.body(),n.ticketId(),n.payload(),n.deduplicationKey(),readAt,n.createdAt(),n.version()+1)); }
    @Override public java.util.Optional<List<NotificationDelivery>> findDeliveries(String id, String user, int offset, int limit) { if (jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE id=? AND recipient_iam_user_id=?", Long.class,id,user)==0) return java.util.Optional.empty(); return java.util.Optional.of(jdbc.query("SELECT * FROM notification_delivery WHERE notification_id=? ORDER BY created_at LIMIT ? OFFSET ?", (rs,row)->new NotificationDelivery(rs.getString("id"), MessageChannel.valueOf(rs.getString("channel")), rs.getString("status"), rs.getInt("attempt_count"), instant(rs.getTimestamp("updated_at")), instant(rs.getTimestamp("next_attempt_at")), rs.getString("last_error_code"), rs.getTimestamp("created_at").toInstant(), instant(rs.getTimestamp("delivered_at"))),id,limit,offset)); }
    @Override public long countDeliveries(String id, String user) { return jdbc.queryForObject("SELECT COUNT(*) FROM notification_delivery d JOIN notification n ON n.id=d.notification_id WHERE n.id=? AND n.recipient_iam_user_id=?",Long.class,id,user); }
    private Notification row(java.sql.ResultSet rs) throws java.sql.SQLException { return new Notification(rs.getString("id"), rs.getString("recipient_iam_user_id"), rs.getString("category"), rs.getString("title"), rs.getString("body"), rs.getString("ticket_id"), payload(rs.getString("payload")), rs.getString("deduplication_key"), instant(rs.getTimestamp("read_at")), rs.getTimestamp("created_at").toInstant(), rs.getLong("version")); }
    private Map<String, String> payload(String value) { try { return json.readValue(value, new TypeReference<Map<String, String>>() {}); } catch (Exception e) { throw new IllegalStateException("Stored notification payload is invalid", e); } }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
}
