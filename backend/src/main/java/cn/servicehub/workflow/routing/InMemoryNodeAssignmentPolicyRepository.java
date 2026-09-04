package cn.servicehub.workflow.routing;

import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryNodeAssignmentPolicyRepository implements NodeAssignmentPolicyRepository {
    private final Map<String, NodeAssignmentPolicy> values = new ConcurrentHashMap<>();
    private final Map<String, NodeAssignmentSnapshot> snapshots = new ConcurrentHashMap<>();
    @Override public Optional<NodeAssignmentPolicy> findActive(String catalog, String node) { return Optional.ofNullable(values.get(catalog + ":" + node)).filter(NodeAssignmentPolicy::enabled); }
    @Override public List<NodeAssignmentPolicy> findByCatalogItemId(String catalog) { return values.values().stream().filter(value -> value.serviceCatalogItemId().equals(catalog)).sorted(java.util.Comparator.comparing(NodeAssignmentPolicy::nodeKey)).toList(); }
    @Override public NodeAssignmentPolicy save(NodeAssignmentPolicy value, long expectedVersion, String actor) {
        String key = value.serviceCatalogItemId() + ":" + value.nodeKey(); NodeAssignmentPolicy current = values.get(key);
        if ((current == null && expectedVersion != 0) || (current != null && current.version() != expectedVersion)) throw new cn.servicehub.workflow.application.WorkflowConflictException();
        NodeAssignmentPolicy saved = new NodeAssignmentPolicy(value.serviceCatalogItemId(), value.nodeKey(), value.mode(),value.queueCode(), value.candidateRoles(), current == null ? 1 : current.version() + 1, value.enabled()); values.put(key, saved); return saved;
    }
    @Override public List<NodeAssignmentSnapshot> findSnapshots(String ticketId) { return snapshots.values().stream().filter(value -> value.ticketId().equals(ticketId)).sorted(java.util.Comparator.comparing(NodeAssignmentSnapshot::capturedAt)).toList(); }
    @Override public void saveSnapshot(NodeAssignmentSnapshot snapshot) { snapshots.put(snapshot.ticketId() + ":" + snapshot.nodeKey(), snapshot); }
}
