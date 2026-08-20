package cn.servicehub.workflow.web;

import cn.servicehub.workflow.domain.WorkflowAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WorkflowActionRequest(
    @NotNull WorkflowAction action,
    @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String targetIamUserId,
    @Size(max = 2000) String comment,
    @Size(max = 1000) String reason,
    @Pattern(regexp = "^(classify|assign|accept|processing|user_feedback|closure)$") String targetNode) {
}
