package cn.servicehub.sla.domain;

import java.util.Optional;
import java.util.List;

public interface TicketSlaTargetRepository {
    Optional<TicketSlaTarget> findByTicketId(String ticketId);
    List<String> findBreachedTicketIds();
    void save(TicketSlaTarget target, Long expectedVersion);
}
