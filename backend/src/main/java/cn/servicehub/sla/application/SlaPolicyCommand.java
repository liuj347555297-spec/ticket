package cn.servicehub.sla.application;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import java.util.Set;

/** No browser-controlled ticket data is used to select a policy. */
public record SlaPolicyCommand(String name, String serviceCatalogItemId, TicketPriority priority, String organizationScopeId,
                               int responseTargetMinutes, int resolutionTargetMinutes, String calendarKey,
                               Set<TicketStatus> pauseStatuses, boolean active, Long expectedVersion) {
}
