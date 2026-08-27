package cn.servicehub.ticket.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRelation;
import cn.servicehub.ticket.domain.TicketRelationRepository;
import cn.servicehub.ticket.domain.TicketRelationType;
import cn.servicehub.ticket.domain.TicketRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies object-level authorization to both ends of every relationship. */
@Service
public class TicketRelationService {
    private final TicketRelationRepository relations;
    private final TicketRepository tickets;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectAuthorizationService authorization;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();

    public TicketRelationService(TicketRelationRepository relations, TicketRepository tickets, CurrentUserProvider currentUserProvider,
                                 ObjectAuthorizationService authorization, AuditEventPublisher audit) {
        this.relations = relations; this.tickets = tickets; this.currentUserProvider = currentUserProvider;
        this.authorization = authorization; this.audit = audit;
    }

    public List<TicketRelationView> list(String ticketId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Ticket source = requireTicket(ticketId);
        require(user, source, ObjectAction.READ);
        return relations.findByTicketId(ticketId).stream().flatMap(relation -> {
            String relatedId = relation.ticketId().equals(ticketId) ? relation.relatedTicketId() : relation.ticketId();
            return tickets.findById(relatedId).stream().filter(related -> canRead(user, related)).map(related ->
                view(ticketId, relation, related));
        }).toList();
    }

    @Transactional
    public TicketRelationView create(String sourceTicketId, String targetTicketId, TicketRelationType type) {
        if (sourceTicketId.equals(targetTicketId)) throw new IllegalArgumentException("A ticket cannot be related to itself");
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Ticket source = requireTicket(sourceTicketId);
        Ticket target = requireTicket(targetTicketId);
        require(user, source, ObjectAction.UPDATE);
        require(user, target, ObjectAction.READ);
        TicketRelation relation = normalize(sourceTicketId, targetTicketId, type, user.iamUserId());
        relations.createIfAbsent(relation); // natural key makes retrying the same request safe
        audit.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_RELATED", "ticket_relation",
            relation.ticketId() + ":" + relation.relatedTicketId() + ":" + relation.relationType(),
            Map.of("sourceTicketId", sourceTicketId, "targetTicketId", targetTicketId, "relationType", type.name())));
        return view(sourceTicketId, relation, target);
    }

    private TicketRelation normalize(String source, String target, TicketRelationType type, String actor) {
        // RELATED is symmetrical and stored canonically so reversed retries do not create duplicates.
        if (type == TicketRelationType.RELATED && source.compareTo(target) > 0) return new TicketRelation(target, source, type, actor, clock.instant());
        return new TicketRelation(source, target, type, actor, clock.instant());
    }

    private TicketRelationView view(String requestedTicketId, TicketRelation relation, Ticket related) {
        String direction = relation.ticketId().equals(requestedTicketId) ? "OUTBOUND" : "INBOUND";
        return new TicketRelationView(relation.relationType(), direction,
            new TicketRelationView.RelatedTicketSummary(related.id(), related.type(), related.status(), related.priority(), related.title()),
            relation.createdByIamUserId(), relation.createdAt());
    }

    private Ticket requireTicket(String ticketId) {
        return tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
    }

    private void require(CurrentUser user, Ticket ticket, ObjectAction action) {
        authorization.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", ticket.id(), action,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
    }

    private boolean canRead(CurrentUser user, Ticket ticket) {
        try { require(user, ticket, ObjectAction.READ); return true; }
        catch (AccessDeniedException ignored) { return false; }
    }

    private String requestId() {
        String value = MDC.get("requestId");
        return value == null ? "system" : value;
    }
}
