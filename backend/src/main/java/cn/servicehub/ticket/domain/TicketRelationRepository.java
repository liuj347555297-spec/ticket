package cn.servicehub.ticket.domain;

import java.util.List;

/** Persistence port for a small, controlled relationship graph; it is not a generic graph API. */
public interface TicketRelationRepository {
    boolean createIfAbsent(TicketRelation relation);

    List<TicketRelation> findByTicketId(String ticketId);
}
