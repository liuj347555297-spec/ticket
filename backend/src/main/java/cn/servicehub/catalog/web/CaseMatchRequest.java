package cn.servicehub.catalog.web;

import cn.servicehub.ticket.domain.TicketTag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** Draft-only matching request; neither a ticket nor a workflow task is created by this endpoint. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CaseMatchRequest(
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]*$") String serviceCatalogItemId,
    @NotNull @Size(max = 50) Map<@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_.-]{0,63}$") String, Object> structuredFields,
    @Size(max = 20) List<@Valid TagRequest> tags,
    @Size(max = 20) List<@NotBlank @Size(max = 128) String> relatedConfigurationItemIds,
    @Size(max = 300) String keywords) {
    public record TagRequest(@NotBlank @Size(max = 51) @Pattern(regexp = "^#[^\\s#]{1,50}$") String name,
                             @NotNull TicketTag.Kind kind) { }
}
