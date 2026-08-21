package cn.servicehub.sla.domain;

import java.time.Instant;

/** Immutable policy snapshot plus mutable, server-calculated SLA clocks for one ticket. */
public record TicketSlaTarget(String ticketId, String policyId, String policyNameSnapshot, String calendarKeySnapshot,
                              Instant responseDueAt, Instant resolutionDueAt, Instant firstRespondedAt, Instant resolvedAt,
                              long pausedSeconds, Instant pauseStartedAt, SlaRiskLevel riskLevel,
                              boolean responseBreached, boolean resolutionBreached, Instant calculatedAt, long version) {
    public TicketSlaTarget {
        if (pausedSeconds < 0) throw new IllegalArgumentException("Paused seconds cannot be negative");
    }
}
