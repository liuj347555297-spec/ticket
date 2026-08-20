package cn.servicehub.catalog.domain;

import cn.servicehub.ticket.domain.TicketType;
import java.util.List;
import java.util.Set;

public record ServiceCatalogItem(String id, String name, String description, CatalogPublicationStatus publicationStatus,
                                 Set<TicketType> supportedTicketTypes, List<FormFieldDefinition> fields) {
    public ServiceCatalogItem {
        supportedTicketTypes = supportedTicketTypes == null ? Set.of() : Set.copyOf(supportedTicketTypes);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public boolean isPublished() {
        return publicationStatus == CatalogPublicationStatus.PUBLISHED;
    }
}
