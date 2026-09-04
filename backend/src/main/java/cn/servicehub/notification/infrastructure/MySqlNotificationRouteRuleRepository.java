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
    @Override public Optional<NotificationRouteRule> findBest(String organization, String event) { return jdbc.query("""
        SELECT r.* FROM notification_channel_route_rule r
          JOIN iam_organization_projection target ON target.iam_organization_id=? AND target.active=TRUE
          JOIN iam_organization_projection scope ON scope.iam_organization_id=r.iam_organization_id AND scope.active=TRUE
         WHERE r.enabled=TRUE AND (r.event_type='*' OR r.event_type=?)
           AND (scope.iam_organization_id=target.iam_organization_id
             OR (r.include_descendants=TRUE AND target.organization_path LIKE CONCAT(scope.organization_path, '/%')))
         ORDER BY r.priority, r.id LIMIT 1
        """, (rs, row) -> new NotificationRouteRule(rs.getString("id"), rs.getString("iam_organization_id"), rs.getBoolean("include_descendants"), rs.getString("event_type"), MessageChannel.valueOf(rs.getString("preferred_channel")), rs.getString("provider_channel_code"), MessageChannel.valueOf(rs.getString("fallback_channel")), rs.getInt("priority"), rs.getBoolean("enabled")), organization, event).stream().findFirst(); }
}
