package cn.servicehub.workflow.application;

import cn.servicehub.workflow.domain.WorkflowAction;

/** All fields are intent only. State, candidates and identity are re-resolved server side. */
public record WorkflowActionCommand(WorkflowAction action, long expectedTicketVersion, String targetIamUserId,
                                    String comment, String reason, String targetNode) {
}
