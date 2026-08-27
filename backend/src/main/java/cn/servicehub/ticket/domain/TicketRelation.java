package cn.servicehub.ticket.domain;

import java.time.Instant;

/** Immutable directed relation retained independently of mutable ticket fields. */
public record TicketRelation(String ticketId, String relatedTicketId, TicketRelationType relationType,
                             String createdByIamUserId, Instant createdAt) {
}
