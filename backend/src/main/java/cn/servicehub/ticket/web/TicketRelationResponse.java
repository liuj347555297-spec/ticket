package cn.servicehub.ticket.web;

import cn.servicehub.ticket.application.TicketRelationView;
import cn.servicehub.ticket.domain.TicketRelationType;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import java.time.Instant;

public record TicketRelationResponse(TicketRelationType relationType, String direction, RelatedTicket relatedTicket,
                                     String createdByIamUserId, Instant createdAt) {
    public static TicketRelationResponse from(TicketRelationView value) {
        var ticket = value.relatedTicket();
        return new TicketRelationResponse(value.relationType(), value.direction(),
            new RelatedTicket(ticket.id(), ticket.type(), ticket.status(), ticket.priority().name(), ticket.title()),
            value.createdByIamUserId(), value.createdAt());
    }
    public record RelatedTicket(String id, TicketType type, TicketStatus status, String priority, String title) { }
}
