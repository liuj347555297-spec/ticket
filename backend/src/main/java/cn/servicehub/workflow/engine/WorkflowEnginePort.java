package cn.servicehub.workflow.engine;

import java.util.List;
import java.util.Set;

/** Explicit anti-corruption boundary around Flowable. Domain services never call Flowable APIs directly. */
public interface WorkflowEnginePort {
    WorkflowEngineInstance start(String ticketId);
    WorkflowEngineInstance advance(String instanceId, String expectedNodeKey);
    void cancel(String instanceId, String reason);
    WorkflowApprovalDefinition resolveControlledJumpApprovalDefinition();
    WorkflowEngineInstance startControlledJumpApproval(String approvalRequestId, String ticketId, String applicantIamUserId,
                                                       WorkflowApprovalDefinition definition, Set<String> candidateIamUserIds, String decisionMode);
    /** Completes one assigned engine task. The request projection is only finalized when processCompleted is true. */
    WorkflowApprovalDecisionResult decideControlledJumpApproval(String approvalEngineInstanceId, String approverIamUserId, String decision);
    /** Returns only live Flowable approval tasks for the caller's server-resolved candidate roles. */
    List<WorkflowApprovalTask> findPendingControlledJumpApprovalTasks(String iamUserId, int offset, int limit);
    /** Lists only the platform-owned, latest deployed definitions; it has no deployment or mutation capability. */
    List<WorkflowProcessDefinition> findPublishedDefinitions();
    WorkflowEngineInstance moveControlledActivity(String lifecycleInstanceId, String expectedSourceNode, String targetNode);
}
