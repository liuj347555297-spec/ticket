package cn.servicehub.integration.domain;

import java.util.List;
import java.util.Optional;

public interface NormalizedAlertRepository {
    Optional<NormalizedAlert> findBySourceAndEventId(String sourceCode, String sourceEventId);
    NormalizedAlert save(NormalizedAlert alert);
    List<NormalizedAlert> findRecent(int limit);
}
