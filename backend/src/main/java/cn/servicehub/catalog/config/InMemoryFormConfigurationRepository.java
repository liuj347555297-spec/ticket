package cn.servicehub.catalog.config;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryFormConfigurationRepository implements FormConfigurationRepository {
    private final ConcurrentHashMap<String, ManagedFormConfiguration> configurations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, FormPublicationRequest> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ManagedFormConfiguration> publishedSnapshots = new ConcurrentHashMap<>();

    @Override public List<ManagedFormConfiguration> findAll() { return configurations.values().stream().sorted(Comparator.comparing(ManagedFormConfiguration::id)).toList(); }
    @Override public Optional<ManagedFormConfiguration> findById(String id) { return Optional.ofNullable(configurations.get(id)); }
    @Override public List<ManagedFormConfiguration> findPublishedHistory(String code) {
        return publishedSnapshots.values().stream().filter(c -> c.code().equals(code))
            .sorted(Comparator.comparingInt(ManagedFormConfiguration::formVersion).reversed()).toList();
    }
    @Override public void savePublishedSnapshot(ManagedFormConfiguration configuration) { publishedSnapshots.put(configuration.code() + ":" + configuration.formVersion(), configuration); }
    @Override public ManagedFormConfiguration save(ManagedFormConfiguration value, long expectedVersion) {
        return configurations.compute(value.id(), (ignored, existing) -> {
            if (existing == null) { if (expectedVersion != 0) throw new FormConfigurationConflictException(); return value; }
            if (existing.version() != expectedVersion) throw new FormConfigurationConflictException();
            return value;
        });
    }
    @Override public FormPublicationRequest savePublicationRequest(FormPublicationRequest request) { requests.put(request.id(), request); return request; }
    @Override public Optional<FormPublicationRequest> findPublicationRequest(String requestId) { return Optional.ofNullable(requests.get(requestId)); }
}
