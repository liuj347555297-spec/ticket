package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.SlaEscalationEventRepository;
import cn.servicehub.sla.domain.SlaRiskLevel;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!mysql")
public class InMemorySlaEscalationEventRepository implements SlaEscalationEventRepository {
    private final ConcurrentHashMap<String, Boolean> keys = new ConcurrentHashMap<>();
    public boolean appendIfAbsent(String ticketId, long targetVersion, String eventCode, SlaRiskLevel riskLevel, Instant occurredAt) { return keys.putIfAbsent(ticketId+":"+targetVersion+":"+eventCode,Boolean.TRUE)==null; }
}
