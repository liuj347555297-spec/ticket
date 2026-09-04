package cn.servicehub.designer;

import cn.servicehub.designer.StudioModels.Draft;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryStudioDraftRepository implements StudioDraftRepository {
    private final ConcurrentHashMap<String, Draft> values = new ConcurrentHashMap<>();
    public List<Draft> list() { return values.values().stream().sorted(Comparator.comparing(Draft::updatedAt).reversed().thenComparing(Draft::id)).toList(); }
    public Optional<Draft> find(String id) { return Optional.ofNullable(values.get(id)); }
    public Draft insert(Draft value) { if (values.putIfAbsent(value.id(), value) != null) throw new StudioConflictException(); return value; }
    public Draft update(Draft value, long expectedVersion) {
        return values.compute(value.id(), (id, existing) -> { if (existing == null || existing.version() != expectedVersion) throw new StudioConflictException(); return value; });
    }
}
