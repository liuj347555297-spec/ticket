package cn.servicehub.integration.domain;

import java.time.Instant;

/** Normalized alert body excludes vendor-specific payloads and all credentials. */
public record NormalizedAlert(String id, String sourceCode, String sourceEventId, String fingerprint,
                              String severity, String title, String configurationItemId, String status,
                              String idempotencyStatus, String ticketId, Instant occurredAt, Instant receivedAt) { }
