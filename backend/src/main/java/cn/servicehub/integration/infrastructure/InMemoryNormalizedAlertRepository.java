package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.NormalizedAlert;
import cn.servicehub.integration.domain.NormalizedAlertRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryNormalizedAlertRepository implements NormalizedAlertRepository {
    private final ConcurrentHashMap<String, NormalizedAlert> alerts = new ConcurrentHashMap<>();
    @Override public Optional<NormalizedAlert> findBySourceAndEventId(String sourceCode, String sourceEventId) { return Optional.ofNullable(alerts.get(sourceCode + ':' + sourceEventId)); }
    @Override public NormalizedAlert save(NormalizedAlert alert) { alerts.putIfAbsent(alert.sourceCode() + ':' + alert.sourceEventId(), alert); return alerts.get(alert.sourceCode() + ':' + alert.sourceEventId()); }
    @Override public List<NormalizedAlert> findRecent(int limit) { return alerts.values().stream().sorted(java.util.Comparator.comparing(NormalizedAlert::receivedAt).reversed()).limit(limit).toList(); }
}
