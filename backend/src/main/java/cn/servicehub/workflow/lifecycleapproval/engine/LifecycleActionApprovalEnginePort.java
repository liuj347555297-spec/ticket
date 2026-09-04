package cn.servicehub.workflow.lifecycleapproval.engine;

import java.util.Set;

/** Dedicated Flowable port for lifecycle action approvals; it deliberately does not expose controlled jumps. */
public interface LifecycleActionApprovalEnginePort {
    LifecycleActionApprovalDefinition resolveDefinition();
    default LifecycleActionApprovalInstance start(String requestId, String ticketId, String applicantIamUserId,
        LifecycleActionApprovalDefinition definition, Set<String> candidateIamUserIds) {
        return start(requestId, ticketId, applicantIamUserId, definition, candidateIamUserIds, "ANY_ONE", 1);
    }
    LifecycleActionApprovalInstance start(String requestId, String ticketId, String applicantIamUserId,
        LifecycleActionApprovalDefinition definition, Set<String> candidateIamUserIds, String decisionMode, int requiredApprovalCount);
    LifecycleActionApprovalDecision decide(String instanceId, String approverIamUserId, String decision);
    void cancelExpired(String instanceId);
}
