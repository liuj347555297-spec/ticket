package cn.servicehub.sla.web;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record SlaPolicyRequest(@NotBlank @Size(max = 120) String name, @Size(max = 64) String serviceCatalogItemId,
                               TicketPriority priority, @Size(max = 128) String organizationScopeId,
                               @Min(1) @Max(10080) int responseTargetMinutes, @Min(1) @Max(43200) int resolutionTargetMinutes,
                               @NotBlank @Size(max = 64) String calendarKey, Set<TicketStatus> pauseStatuses,
                               boolean active, Long expectedVersion) { }
