package cn.servicehub.ticket.web;

import cn.servicehub.ticket.domain.TicketRelationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record TicketRelationRequest(
    @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String targetTicketId,
    @NotNull TicketRelationType relationType) {
}
