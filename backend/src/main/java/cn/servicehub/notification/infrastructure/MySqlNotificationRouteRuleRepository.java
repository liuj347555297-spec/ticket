package cn.servicehub.notification.infrastructure;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.NotificationRouteRule;
import cn.servicehub.notification.domain.NotificationRouteRuleRepository;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("mysql")
public class MySqlNotificationRouteRuleRepository implements NotificationRouteRuleRepository {
    private final JdbcTemplate jdbc;
    public MySqlNotificationRouteRuleRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<NotificationRouteRule> findBest(String organization, String event) { return jdbc.query("SELECT * FROM notification_channel_route_rule WHERE iam_organization_id=? AND enabled=TRUE AND (event_type='*' OR event_type=?) ORDER BY priority, id LIMIT 1", (rs, row) -> new NotificationRouteRule(rs.getString("id"), rs.getString("iam_organization_id"), rs.getBoolean("include_descendants"), rs.getString("event_type"), MessageChannel.valueOf(rs.getString("preferred_channel")), rs.getString("provider_channel_code"), MessageChannel.valueOf(rs.getString("fallback_channel")), rs.getInt("priority"), rs.getBoolean("enabled")), organization, event).stream().findFirst(); }
}
