package cn.servicehub.catalog.web;

import cn.servicehub.ticket.domain.TicketTag;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RuleMatchRequest(
    @NotBlank @Pattern(regexp = "^SC-[A-Za-z0-9_-]{3,60}$") String serviceCatalogItemId,
    @NotNull @Min(1) Integer formVersion,
    @Size(max = 200) String title,
    @Size(max = 4000) String description,
    @NotNull @Size(max = 50) Map<@Pattern(regexp = "^[a-z][a-z0-9_]{0,63}$") String, Object> structuredFields,
    @Size(max = 20) List<@Valid TagRequest> tags,
    @Size(max = 20) List<@NotBlank @Size(max = 128) String> relatedConfigurationItemIds) {
    public record TagRequest(@NotBlank @Size(max = 51) @Pattern(regexp = "^#[^\\s#<>]{1,50}$") String name,
                             @NotNull TicketTag.Kind kind) { }
}
