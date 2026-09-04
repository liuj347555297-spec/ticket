package cn.servicehub.workflow.team;

import java.time.Instant;

public record SupportQueueMember(String iamUserId, SupportQueueMemberRole role, Instant effectiveFrom, Instant effectiveUntil) {
    public boolean activeAt(Instant at) { return !effectiveFrom.isAfter(at) && (effectiveUntil == null || effectiveUntil.isAfter(at)); }
}
