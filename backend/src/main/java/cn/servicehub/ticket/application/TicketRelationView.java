package cn.servicehub.ticket.application;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketRelationType;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import java.time.Instant;

/** Minimal, authorized summary returned for one linked ticket. */
public record TicketRelationView(TicketRelationType relationType, String direction, RelatedTicketSummary relatedTicket,
                                 String createdByIamUserId, Instant createdAt) {
    public record RelatedTicketSummary(String id, TicketType type, TicketStatus status, TicketPriority priority, String title) { }
}
