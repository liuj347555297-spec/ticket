package cn.servicehub.workflow.engine;

import java.util.List;

/** Display-only projection: never an executable definition or a deployment command. */
public record WorkflowBpmnDiagram(String processKey, String processDefinitionId, Integer version, String bpmnXml,
                                  String layoutSource, List<String> activeNodeIds, List<String> completedNodeIds,
                                  String availability) {
    public static WorkflowBpmnDiagram legacy() {
        return new WorkflowBpmnDiagram("", null, null, null, "NONE", List.of(), List.of(), "UNAVAILABLE_LEGACY");
    }
}
