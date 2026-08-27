package cn.servicehub.integration.domain;

import java.util.List;
import java.util.Optional;

public interface ExternalConnectionConfigurationRepository {
    List<ExternalConnectionConfiguration> findAll();
    Optional<ExternalConnectionConfiguration> findByCode(String code);
}
