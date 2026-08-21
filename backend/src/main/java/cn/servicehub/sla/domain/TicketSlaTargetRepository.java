package cn.servicehub.sla.domain;

import java.util.Optional;

public interface TicketSlaTargetRepository {
    Optional<TicketSlaTarget> findByTicketId(String ticketId);
    void save(TicketSlaTarget target, Long expectedVersion);
}
