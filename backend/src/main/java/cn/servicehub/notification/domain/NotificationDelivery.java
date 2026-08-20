package cn.servicehub.notification.domain;

import java.time.Instant;

/** Sanitised delivery state. Provider identifiers and raw provider responses are intentionally absent. */
public record NotificationDelivery(String id, MessageChannel channel, String state, int attemptCount,
                                   Instant lastAttemptAt, Instant nextRetryAt, String terminalReasonCode,
                                   Instant createdAt, Instant deliveredAt) { }
