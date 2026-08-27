package cn.servicehub.ticket.web;

import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import cn.servicehub.ticket.domain.TicketType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Only requester-controlled business input is accepted. Identity, queue, priority and status are excluded by design. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record TicketCreateRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$") String serviceCatalogItemId,
    @Min(1) int serviceCatalogFormVersion,
    @NotNull TicketType type,
    @NotBlank @Size(min = 4, max = 200) String title,
    @NotBlank @Size(max = 16000) String description,
    TicketDescriptionFormat descriptionFormat,
    @NotNull @Size(max = 50) Map<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{0,63}$") String, Object> structuredFields,
    @Size(max = 20) List<@Valid TagRequest> tags,
    @Size(max = 20) List<@NotBlank @Size(max = 128) String> relatedConfigurationItemIds) {

    public record TagRequest(
        @NotBlank @Size(max = 51) @Pattern(regexp = "^#[^\\s#]{1,50}$") String name,
        @NotNull TicketTag.Kind kind) {
    }
}
