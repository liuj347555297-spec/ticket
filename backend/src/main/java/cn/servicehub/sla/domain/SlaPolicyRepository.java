package cn.servicehub.sla.domain;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyRepository {
    List<SlaPolicy> findAll();
    List<SlaPolicy> findActive();
    Optional<SlaPolicy> findById(String id);
    SlaPolicy save(SlaPolicy policy, Long expectedVersion);
}
