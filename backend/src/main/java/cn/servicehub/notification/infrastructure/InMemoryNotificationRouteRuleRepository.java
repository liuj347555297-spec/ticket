package cn.servicehub.notification.infrastructure;

import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.NotificationRouteRule;
import cn.servicehub.notification.domain.NotificationRouteRuleRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!mysql")
public class InMemoryNotificationRouteRuleRepository implements NotificationRouteRuleRepository {
    private final List<NotificationRouteRule> rules = List.of(
        new NotificationRouteRule("ROUTE-IT-WPS", "org-it", false, "*", MessageChannel.WPS_IM, "wps-it-service-desk", MessageChannel.IN_APP, 10, true),
        new NotificationRouteRule("ROUTE-FINANCE-WPS", "org-finance", false, "*", MessageChannel.WPS_IM, "wps-finance-service-desk", MessageChannel.IN_APP, 10, true));
    @Override public Optional<NotificationRouteRule> findBest(String organization, String eventType) { return rules.stream().filter(r -> r.enabled() && r.iamOrganizationId().equals(organization) && (r.eventType().equals("*") || r.eventType().equals(eventType))).min(Comparator.comparingInt(NotificationRouteRule::priority)); }
}
