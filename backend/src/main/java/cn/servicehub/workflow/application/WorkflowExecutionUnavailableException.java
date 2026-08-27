package cn.servicehub.workflow.application;

/** Safe boundary for an engine or persistence failure during a high-risk workflow execution. */
public class WorkflowExecutionUnavailableException extends RuntimeException {
    public WorkflowExecutionUnavailableException() { super("Workflow execution is temporarily unavailable"); }
}
