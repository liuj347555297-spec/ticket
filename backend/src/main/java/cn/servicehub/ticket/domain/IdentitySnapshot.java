package cn.servicehub.ticket.domain;

import java.time.Instant;

/** Immutable identity information captured at the business event time. */
public record IdentitySnapshot(String iamUserId, String displayName, String organizationId, String organizationName,
                               String positionName, Instant capturedAt) {
    /** Compatibility constructor for historical snapshots created before the IAM organization id was retained. */
    public IdentitySnapshot(String iamUserId, String displayName, String organizationName, String positionName, Instant capturedAt) {
        this(iamUserId, displayName, null, organizationName, positionName, capturedAt);
    }
}
