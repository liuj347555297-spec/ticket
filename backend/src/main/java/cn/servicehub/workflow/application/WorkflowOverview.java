package cn.servicehub.workflow.application;

import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import java.util.List;

public record WorkflowOverview(WorkflowInstance instance, List<WorkflowTask> tasks, List<WorkflowComment> comments) {
}
