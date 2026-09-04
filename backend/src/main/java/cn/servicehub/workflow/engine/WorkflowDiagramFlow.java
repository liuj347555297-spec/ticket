package cn.servicehub.workflow.engine;

/** A directed Flowable BPMN sequence flow between two returned nodes. */
public record WorkflowDiagramFlow(String id, String sourceNodeId, String targetNodeId) { }
