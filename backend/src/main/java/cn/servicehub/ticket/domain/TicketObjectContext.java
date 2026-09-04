package cn.servicehub.ticket.domain;

import java.util.Set;

/** Server-resolved ticket facts used by both collection SQL and object authorization. */
public record TicketObjectContext(String requesterIamUserId, String requesterOrganizationId,
                                  String serviceCatalogItemId, String serviceSystemCode,
                                  Set<String> configurationItemIds, boolean activeParticipant) {
    public TicketObjectContext {
        configurationItemIds = configurationItemIds == null ? Set.of() : Set.copyOf(configurationItemIds);
    }
}
