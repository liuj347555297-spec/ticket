package cn.servicehub.workflow.engine;

import java.time.Instant;

/** Read-only published Flowable definition metadata. Deployment content is never supplied by a browser. */
public record WorkflowProcessDefinition(String processKey, String processDefinitionId, String name, int version,
                                        String deploymentId, Instant deployedAt) { }
