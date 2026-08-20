package cn.servicehub.notification.domain;

import java.util.Optional;

/** Read-only runtime port; configuration changes belong to a separately authorised administration workflow. */
public interface NotificationRouteRuleRepository {
    Optional<NotificationRouteRule> findBest(String iamOrganizationId, String eventType);
}
