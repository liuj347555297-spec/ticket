package cn.servicehub.workflow.domain;

import cn.servicehub.ticket.domain.IdentitySnapshot;
import java.util.List;
import java.util.Optional;

public interface TicketWorkflowRepository {
    void create(WorkflowInstance instance, WorkflowTask initialTask);
    Optional<WorkflowInstance> findInstance(String ticketId);
    boolean updateInstance(WorkflowInstance replacement, long expectedVersion);
    Optional<WorkflowTask> findOpenTask(String ticketId, String nodeKey);
    void saveTask(WorkflowTask task);
    boolean claimOpenTask(String taskId, long expectedVersion, String queueCode, String assigneeIamUserId, java.time.Instant at);
    long countOpenTasksByQueue(String queueCode);
    long countClaimedTasksByQueue(String queueCode);
    long migrateOpenTasks(String sourceQueueCode, String targetQueueCode);
    List<WorkflowTask> findActiveTasksByQueue(String queueCode);
    boolean migrateOpenTask(String taskId,long expectedVersion,String sourceQueueCode,String targetQueueCode,String ticketId,long expectedTicketVersion,long expectedWorkflowVersion,java.time.Instant at);
    List<WorkflowTask> findTasks(String ticketId);
    List<String> findTodoTicketIds(String iamUserId, java.util.Set<String> authorities);
    List<String> findCompletedTicketIds(String iamUserId);
    void addCoHandler(String ticketId, String iamUserId, java.time.Instant at);
    boolean hasCoHandler(String ticketId, String iamUserId);
    void addDelegation(TicketDelegation delegation);
    boolean hasActiveDelegation(String ticketId, String delegatorIamUserId, String delegateIamUserId, java.time.Instant at);
    void replacePrimaryParticipant(String ticketId, IdentitySnapshot identity, java.time.Instant at);
    void addCoHandlerParticipant(String ticketId, IdentitySnapshot identity, java.time.Instant at);
    void clearPrimaryParticipant(String ticketId, java.time.Instant at);
    List<WorkflowParticipant> findActiveParticipants(String ticketId);
    void addComment(WorkflowComment comment);
    List<WorkflowComment> findComments(String ticketId);
    void addHandoverRequest(HandoverRequest request);
    List<HandoverRequest> findHandoverRequests(String ticketId);
    Optional<HandoverRequest> findHandoverRequest(String ticketId, String requestId);
    Optional<HandoverRequest> findHandoverRequestById(String requestId);
    /** Atomically applies a Flowable-confirmed result if the ticket/workflow source has not changed. */
    boolean finalizeHandoverRequest(String ticketId, String requestId, String decision, String reason, java.time.Instant decidedAt,
                                    long sourceTicketVersion, long sourceWorkflowVersion);
    void addCoHandlerRequest(CoHandlerRequest request);
    List<CoHandlerRequest> findCoHandlerRequests(String ticketId);
    Optional<CoHandlerRequest> findCoHandlerRequest(String ticketId, String requestId);
    Optional<CoHandlerRequest> findCoHandlerRequestById(String requestId);
    /** Applies a Flowable-confirmed result only when the ticket/workflow source is unchanged. */
    boolean finalizeCoHandlerRequest(String ticketId, String requestId, String decision, String reason, java.time.Instant decidedAt,
                                     long sourceTicketVersion, long sourceWorkflowVersion);
    void addJumpRequest(ControlledJumpRequest request);
    List<ControlledJumpRequest> findJumpRequests(String ticketId);
    java.util.Optional<ControlledJumpRequest> findJumpRequest(String ticketId, String requestId);
    /** Global lookup is only for Flowable task-to-request correlation; authorization remains per ticket. */
    java.util.Optional<ControlledJumpRequest> findJumpRequestById(String requestId);
    /** Finalizes an approval projection only after the corresponding Flowable process ended. */
    boolean finalizeJumpRequestApproval(String ticketId, String requestId, String decision, String approverIamUserId, String reason, java.time.Instant decidedAt);
    void appendApprovalDecision(ApprovalDecisionRecord decision);
    List<ApprovalDecisionRecord> findApprovalDecisions(String ticketId, String requestId);
    /** Atomically reserves an approved request before a Flowable state change; the enclosing transaction rolls it back on failure. */
    boolean claimJumpExecution(String ticketId, String requestId, long sourceTicketVersion, long sourceWorkflowVersion,
                               String executorIamUserId, java.time.Instant startedAt);
    boolean completeJumpExecution(String ticketId, String requestId, String executedFromNode, String executedToNode,
                                  java.time.Instant executedAt);
    /** Releases a reservation only when the enclosing execution failed before completion. MySQL rolls this back with the transaction; the in-memory profile applies it explicitly. */
    boolean releaseJumpExecution(String ticketId, String requestId);
    void appendEvent(String ticketId, String action, String actorIamUserId, String requestId,
                     java.util.Map<String, String> attributes, java.time.Instant occurredAt);
    List<WorkflowEvent> findEvents(String ticketId);
}
