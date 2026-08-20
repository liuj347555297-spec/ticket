package cn.servicehub.workflow.domain;

import java.util.List;
import java.util.Optional;

public interface TicketWorkflowRepository {
    void create(WorkflowInstance instance, WorkflowTask initialTask);
    Optional<WorkflowInstance> findInstance(String ticketId);
    boolean updateInstance(WorkflowInstance replacement, long expectedVersion);
    Optional<WorkflowTask> findOpenTask(String ticketId, String nodeKey);
    void saveTask(WorkflowTask task);
    List<WorkflowTask> findTasks(String ticketId);
    void addCoHandler(String ticketId, String iamUserId, java.time.Instant at);
    boolean hasCoHandler(String ticketId, String iamUserId);
    void addComment(WorkflowComment comment);
    List<WorkflowComment> findComments(String ticketId);
    void addJumpRequest(ControlledJumpRequest request);
    void appendEvent(String ticketId, String action, String actorIamUserId, String requestId,
                     java.util.Map<String, String> attributes, java.time.Instant occurredAt);
}
