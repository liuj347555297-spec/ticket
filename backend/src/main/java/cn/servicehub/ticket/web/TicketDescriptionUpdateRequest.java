package cn.servicehub.ticket.web;

import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Only used to bind already-uploaded, scan-clean inline image references immediately after creation. */
public record TicketDescriptionUpdateRequest(
    @NotBlank @Size(max = 16000) String description,
    @NotNull TicketDescriptionFormat descriptionFormat) {
}
