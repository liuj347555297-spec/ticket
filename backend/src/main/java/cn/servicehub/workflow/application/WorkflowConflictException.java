package cn.servicehub.workflow.application;

public class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException() { super("Workflow was changed by another action"); }
}
