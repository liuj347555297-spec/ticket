package cn.servicehub.notification.domain;

/** Server-managed routing rule. Its organisation and channel values are never accepted from a browser. */
public record NotificationRouteRule(String id, String iamOrganizationId, boolean includeDescendants, String eventType,
                                    MessageChannel preferredChannel, String providerChannelCode,
                                    MessageChannel fallbackChannel, int priority, boolean enabled) { }
