package cn.servicehub.workflow.engine;

/**
 * Result of one immutable human decision in a multi-instance approval process.
 * A request projection may only be finalized after Flowable has closed the process.
 */
public record WorkflowApprovalDecisionResult(String engineTaskId, boolean processCompleted, String finalDecision) {
    public WorkflowApprovalDecisionResult {
        if (engineTaskId == null || engineTaskId.isBlank() || (processCompleted &&
            !java.util.Set.of("APPROVED", "REJECTED").contains(finalDecision)) || (!processCompleted && finalDecision != null)) {
            throw new IllegalArgumentException("Approval decision result is invalid");
        }
    }
}
