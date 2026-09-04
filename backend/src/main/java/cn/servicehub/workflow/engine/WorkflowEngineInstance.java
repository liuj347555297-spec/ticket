package cn.servicehub.workflow.engine;

/**
 * Engine state returned after a server-side command.  The definition fields are an immutable
 * launch snapshot: a later BPMN deployment must never make an existing ticket appear to have
 * been started by a newer process version.
 */
public record WorkflowEngineInstance(String instanceId, String nodeKey, String taskId,
                                     String processDefinitionId, int processDefinitionVersion) {
    /** Compatibility constructor for test doubles and legacy adapters; production Flowable calls use the full snapshot. */
    public WorkflowEngineInstance(String instanceId, String nodeKey, String taskId) {
        this(instanceId, nodeKey, taskId, "LEGACY_UNRECORDED", 0);
    }
}
