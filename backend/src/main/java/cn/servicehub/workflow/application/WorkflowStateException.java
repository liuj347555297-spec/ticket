package cn.servicehub.workflow.application;

public class WorkflowStateException extends RuntimeException {
    public WorkflowStateException() { super("Action is not valid for the current workflow state"); }
}
