package cn.servicehub.catalog.application;

import cn.servicehub.ticket.domain.TicketTag;
import java.util.List;
import java.util.Map;

public record CaseMatchCommand(String serviceCatalogItemId, Map<String, Object> structuredFields,
                               List<TicketTag> tags, List<String> relatedConfigurationItemIds, String keywords) {
    public CaseMatchCommand {
        structuredFields = structuredFields == null ? Map.of() : Map.copyOf(structuredFields);
        tags = tags == null ? List.of() : List.copyOf(tags);
        relatedConfigurationItemIds = relatedConfigurationItemIds == null ? List.of() : List.copyOf(relatedConfigurationItemIds);
        keywords = keywords == null ? "" : keywords.trim();
    }
}
