package cn.servicehub.workflow.lifecycleapproval.engine;
public record LifecycleActionApprovalDecision(String taskId, boolean processCompleted, String finalDecision) { }
