package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.ExternalConnectionConfiguration;
import cn.servicehub.integration.domain.ExternalConnectionConfigurationRepository;
import cn.servicehub.integration.domain.ExternalSystemType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development metadata contains no credential and keeps every live callback disabled. */
@Repository
@Profile("!mysql")
public class InMemoryExternalConnectionConfigurationRepository implements ExternalConnectionConfigurationRepository {
    private final List<ExternalConnectionConfiguration> configurations = List.of(
        new ExternalConnectionConfiguration("CMDB", "CMDB 只读投影", ExternalSystemType.CMDB, "https://cmdb.example.invalid", "", false, 1500, 60, List.of(), Instant.parse("2026-01-01T00:00:00Z")),
        new ExternalConnectionConfiguration("MONITORING", "监控告警", ExternalSystemType.MONITORING, "https://monitoring.example.invalid", "", false, 1500, 60, List.of(), Instant.parse("2026-01-01T00:00:00Z")),
        new ExternalConnectionConfiguration("LOG", "日志平台", ExternalSystemType.LOG_PLATFORM, "https://logs.example.invalid", "", false, 1500, 60, List.of(), Instant.parse("2026-01-01T00:00:00Z")),
        new ExternalConnectionConfiguration("APM", "应用性能监控", ExternalSystemType.APM, "https://apm.example.invalid", "", false, 1500, 60, List.of(), Instant.parse("2026-01-01T00:00:00Z")));
    @Override public List<ExternalConnectionConfiguration> findAll() { return configurations; }
    @Override public Optional<ExternalConnectionConfiguration> findByCode(String code) { return configurations.stream().filter(item -> item.code().equals(code)).findFirst(); }
}
