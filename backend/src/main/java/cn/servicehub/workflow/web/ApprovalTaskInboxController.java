package cn.servicehub.workflow.web;

import cn.servicehub.workflow.application.TicketWorkflowService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Approval work is enumerated from live Flowable candidate tasks, never from a browser supplied identifier. */
@RestController
@Validated
@RequestMapping("/api/v1/workflow")
public class ApprovalTaskInboxController {
    private final TicketWorkflowService workflowService;
    public ApprovalTaskInboxController(TicketWorkflowService workflowService) { this.workflowService = workflowService; }

    @GetMapping("/approval-tasks")
    ApprovalTaskInboxResponse list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {
        return ApprovalTaskInboxResponse.from(workflowService.approvalInbox(page, pageSize));
    }
}
