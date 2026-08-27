package cn.servicehub.workflow.engine;

/** A concrete, already-published Flowable approval definition resolved before an instance starts. */
public record WorkflowApprovalDefinition(String processKey, String processDefinitionId, int version) {
}
