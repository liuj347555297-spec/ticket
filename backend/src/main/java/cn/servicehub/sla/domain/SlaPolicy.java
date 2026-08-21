package cn.servicehub.sla.domain;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.Set;

/** Versioned, server-owned SLA policy. Calendar support is deliberately explicit for later expansion. */
public record SlaPolicy(String id, String name, String serviceCatalogItemId, TicketPriority priority,
                        String organizationScopeId, int responseTargetMinutes, int resolutionTargetMinutes,
                        String calendarKey, Set<TicketStatus> pauseStatuses, boolean active, long version,
                        Instant createdAt, Instant updatedAt) {
    public SlaPolicy {
        pauseStatuses = pauseStatuses == null ? Set.of() : Set.copyOf(pauseStatuses);
        if (responseTargetMinutes < 1 || resolutionTargetMinutes < 1) throw new IllegalArgumentException("SLA targets must be positive");
        if (resolutionTargetMinutes < responseTargetMinutes) throw new IllegalArgumentException("Resolution target cannot precede response target");
        if (calendarKey == null || calendarKey.isBlank()) throw new IllegalArgumentException("Calendar key is required");
    }
}
