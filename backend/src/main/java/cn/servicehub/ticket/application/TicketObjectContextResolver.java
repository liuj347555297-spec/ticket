package cn.servicehub.ticket.application;

import cn.servicehub.servicesystem.domain.ServiceSystemRepository;
import cn.servicehub.ticket.domain.TicketObjectContext;
import cn.servicehub.ticket.domain.TicketRepository;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Loads ticket authorization facts from server-owned records, never from a request DTO. */
@Component
public class TicketObjectContextResolver {
    private final TicketRepository tickets; private final ServiceSystemRepository systems;
    public TicketObjectContextResolver(TicketRepository tickets, ServiceSystemRepository systems) {
        this.tickets = tickets; this.systems = systems;
    }
    public TicketObjectContext resolve(String ticketId) {
        return resolveForScope(ticketId);
    }
    public TicketObjectContext resolveForScope(String ticketId) {
        var ticket = tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        String systemCode = systems.findTicketSnapshot(ticketId).map(value -> value.systemCode()).orElse(null);
        return new TicketObjectContext(ticket.requester().iamUserId(), ticket.requester().organizationId(),
            ticket.serviceCatalogItem().id(), systemCode, Set.copyOf(ticket.relatedConfigurationItemIds()), false);
    }
}
