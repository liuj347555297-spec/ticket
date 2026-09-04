package cn.servicehub.workflow.engine;

/** A stable Flowable BPMN node identifier and display metadata for a read-only diagram. */
public record WorkflowDiagramNode(String id, String label, String type) { }
