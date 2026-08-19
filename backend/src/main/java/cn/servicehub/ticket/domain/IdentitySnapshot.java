package cn.servicehub.ticket.domain;

import java.time.Instant;

/** Immutable identity information captured at the business event time. */
public record IdentitySnapshot(String iamUserId, String displayName, String organizationName,
                               String positionName, Instant capturedAt) {
}
