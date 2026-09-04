package cn.servicehub.workflow.routing;

import java.util.Optional;
import java.util.List;

public interface NodeAssignmentPolicyRepository {
    Optional<NodeAssignmentPolicy> findActive(String serviceCatalogItemId, String nodeKey);
    List<NodeAssignmentPolicy> findByCatalogItemId(String serviceCatalogItemId);
    NodeAssignmentPolicy save(NodeAssignmentPolicy policy, long expectedVersion, String actorIamUserId);
    List<NodeAssignmentSnapshot> findSnapshots(String ticketId);
    void saveSnapshot(NodeAssignmentSnapshot snapshot);
}
