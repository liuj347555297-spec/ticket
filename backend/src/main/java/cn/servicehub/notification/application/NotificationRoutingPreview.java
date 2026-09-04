package cn.servicehub.notification.application;

import cn.servicehub.notification.domain.MessageChannel;

/** Sanitised, read-only resolution result. It never contains a route target, recipient or credential. */
public record NotificationRoutingPreview(String organizationIamOrganizationId, String event, String resolution,
                                         MessageChannel requestedChannel, MessageChannel resolvedChannel,
                                         boolean inAppFallbackApplied, MatchedRule matchedRule) {
    public record MatchedRule(String id, int version, int priority, int aggregationWindowSeconds,
                              boolean includeDescendants, String lifecycleStatus) { }
}
