package cn.servicehub.ticket.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Initial ticket aggregate. Its requester snapshot and creation metadata are immutable; later
 * workflow actions will create new state transitions instead of trusting browser supplied values.
 */
public record Ticket(String id, TicketType type, TicketStatus status, TicketPriority priority,
                     String title, String description, Map<String, Object> structuredFields,
                     List<TicketTag> tags, List<String> relatedConfigurationItemIds,
                     IdentitySnapshot requester, ServiceCatalogSummary serviceCatalogItem,
                     Instant createdAt, Instant updatedAt, long version) {
    public Ticket {
        structuredFields = structuredFields == null ? Map.of() : Map.copyOf(structuredFields);
        tags = tags == null ? List.of() : List.copyOf(tags);
        relatedConfigurationItemIds = relatedConfigurationItemIds == null ? List.of() : List.copyOf(relatedConfigurationItemIds);
    }

    public boolean matchesKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.toLowerCase(java.util.Locale.ROOT);
        return title.toLowerCase(java.util.Locale.ROOT).contains(normalized)
            || description.toLowerCase(java.util.Locale.ROOT).contains(normalized)
            || tags.stream().anyMatch(tag -> tag.name().toLowerCase(java.util.Locale.ROOT).contains(normalized));
    }
}
