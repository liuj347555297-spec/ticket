package cn.servicehub.ticket.web;

import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketDescriptionFormat;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;

public record TicketResponse(String id, TicketType type, TicketStatus status, TicketPriority priority,
                             String title, String description, TicketDescriptionFormat descriptionFormat, String descriptionHtml, IdentitySnapshot requester,
                             ServiceCatalogSummary serviceCatalogItem, List<TicketTag> tags,
                             Instant createdAt, Instant updatedAt, long version) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(ticket.id(), ticket.type(), ticket.status(), ticket.priority(), ticket.title(),
            ticket.description(), ticket.descriptionFormat(), ticket.descriptionHtml(), ticket.requester(), ticket.serviceCatalogItem(), ticket.tags(), ticket.createdAt(),
            ticket.updatedAt(), ticket.version());
    }
}
