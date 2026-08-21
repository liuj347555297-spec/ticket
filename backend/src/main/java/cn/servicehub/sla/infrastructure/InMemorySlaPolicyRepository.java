package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.SlaPolicy;
import cn.servicehub.sla.domain.SlaPolicyRepository;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemorySlaPolicyRepository implements SlaPolicyRepository {
    private final ConcurrentHashMap<String, SlaPolicy> policies = new ConcurrentHashMap<>();
    public InMemorySlaPolicyRepository() {
        Instant seed = Instant.parse("2026-01-01T00:00:00Z");
        SlaPolicy policy = new SlaPolicy("00000000-0000-4000-8000-000000000007", "默认 P3 服务台", null, TicketPriority.P3, null, 60, 480, "24X7", Set.of(TicketStatus.ON_HOLD, TicketStatus.PENDING_USER_FEEDBACK), true, 0, seed, seed);
        policies.put(policy.id(), policy);
    }
    public List<SlaPolicy> findAll() { return policies.values().stream().sorted(java.util.Comparator.comparing(SlaPolicy::name)).toList(); }
    public List<SlaPolicy> findActive() { return findAll().stream().filter(SlaPolicy::active).toList(); }
    public Optional<SlaPolicy> findById(String id) { return Optional.ofNullable(policies.get(id)); }
    public SlaPolicy save(SlaPolicy policy, Long expectedVersion) { policies.compute(policy.id(), (ignored, old) -> { if (old != null && (expectedVersion == null || old.version() != expectedVersion)) throw new IllegalStateException("SLA policy version conflict"); return policy; }); return policy; }
}
