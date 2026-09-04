package cn.servicehub.workflow.team;

import java.util.List;
import java.util.Optional;

public interface SupportQueueRepository {
    List<SupportQueue> findAll();
    Optional<SupportQueue> findByCode(String code);
    SupportQueue save(SupportQueue value, long expectedVersion, String actorIamUserId);
    void saveRoutingSnapshot(WorkflowQueueRoutingSnapshot snapshot);
    List<WorkflowQueueRoutingSnapshot> findRoutingSnapshots(String ticketId);
}
