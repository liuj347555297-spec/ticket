package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.TicketSlaTarget;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryTicketSlaTargetRepository implements TicketSlaTargetRepository {
    private final ConcurrentHashMap<String, TicketSlaTarget> targets = new ConcurrentHashMap<>();
    public Optional<TicketSlaTarget> findByTicketId(String ticketId) { return Optional.ofNullable(targets.get(ticketId)); }
    public void save(TicketSlaTarget target, Long expectedVersion) { targets.compute(target.ticketId(), (ignored, old) -> { if (old == null && expectedVersion != null || old != null && (expectedVersion == null || old.version() != expectedVersion)) throw new IllegalStateException("SLA target version conflict"); return target; }); }
}
