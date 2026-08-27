package cn.servicehub.workflow.application;

import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowEvent;
import cn.servicehub.workflow.domain.WorkflowParticipant;
import java.util.List;

/** Authorized workflow read model for the ticket detail page. */
public record WorkflowOverview(WorkflowInstance instance, List<WorkflowTask> tasks, List<WorkflowComment> comments,
                               List<WorkflowAvailableAction> availableActions, List<WorkflowEvent> events,
                               List<WorkflowParticipant> participants, List<cn.servicehub.workflow.domain.ControlledJumpRequest> approvalRequests,
                               List<cn.servicehub.workflow.domain.ApprovalDecisionRecord> approvalDecisions,
                               List<ControlledJumpAvailableAction> controlledJumpActions) {
}
