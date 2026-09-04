package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.WorkCalendarRepository;
import cn.servicehub.sla.domain.WorkCalendarVersion;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryWorkCalendarRepository implements WorkCalendarRepository {
    private final Map<String, WorkCalendarVersion> current = new ConcurrentHashMap<>();
    private final Map<String, WorkCalendarVersion> versions = new ConcurrentHashMap<>();
    public InMemoryWorkCalendarRepository() { save(new WorkCalendarVersion("24X7", 1, "7×24 服务日历", "UTC", true, java.util.Set.copyOf(java.util.List.of(DayOfWeek.values())), null, null, java.util.Set.of())); }
    public Optional<WorkCalendarVersion> findCurrent(String key) { return Optional.ofNullable(current.get(key)); }
    public Optional<WorkCalendarVersion> findVersion(String key, int version) { return Optional.ofNullable(versions.get(key + "@" + version)); }
    public List<WorkCalendarVersion> findCurrentActive() { return current.values().stream().sorted(java.util.Comparator.comparing(WorkCalendarVersion::key)).toList(); }
    public synchronized WorkCalendarVersion createVersion(WorkCalendarVersion candidate) {
        WorkCalendarVersion old = current.get(candidate.key());
        if (old != null && candidate.version() != old.version() + 1) throw new IllegalStateException("Calendar version conflict");
        if (old == null && candidate.version() != 1) throw new IllegalStateException("Calendar must start at version 1");
        save(candidate); return candidate;
    }
    private void save(WorkCalendarVersion value) { current.put(value.key(), value); versions.put(value.snapshotKey(), value); }
}
