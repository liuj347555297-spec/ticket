package cn.servicehub.workflow.engine;

import java.util.List;

/**
 * A curated, read-only view of the platform-owned ticket lifecycle BPMN definition.
 * It intentionally contains neither raw BPMN XML nor deployment mutation metadata.
 */
public record WorkflowDiagramPreview(String processKey, String processDefinitionId, String name, int version,
                                     List<WorkflowDiagramNode> nodes, List<WorkflowDiagramFlow> flows) {
    public WorkflowDiagramPreview {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        flows = flows == null ? List.of() : List.copyOf(flows);
    }
}
