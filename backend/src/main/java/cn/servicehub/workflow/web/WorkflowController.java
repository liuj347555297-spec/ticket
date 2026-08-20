package cn.servicehub.workflow.web;

import cn.servicehub.ticket.application.TicketService;
import cn.servicehub.ticket.web.TicketResponse;
import cn.servicehub.workflow.application.TicketWorkflowService;
import cn.servicehub.workflow.application.WorkflowActionCommand;
import cn.servicehub.workflow.application.WorkflowOverview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/tickets/{ticketId}/workflow")
public class WorkflowController {
    private final TicketWorkflowService workflowService;
    private final TicketService ticketService;
    public WorkflowController(TicketWorkflowService workflowService, TicketService ticketService) { this.workflowService = workflowService; this.ticketService = ticketService; }

    @GetMapping
    WorkflowOverview overview(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) {
        ticketService.get(ticketId); // performs server-side object authorization before collaboration data is disclosed
        return workflowService.overview(ticketId);
    }

    @PostMapping("/actions")
    TicketResponse action(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
                          @RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp = "^\"?[0-9]+\"?$") String ifMatch,
                          @Valid @RequestBody WorkflowActionRequest request) {
        long expectedVersion = Long.parseLong(ifMatch.replace("\"", ""));
        return TicketResponse.from(workflowService.act(ticketId, new WorkflowActionCommand(request.action(), expectedVersion,
            request.targetIamUserId(), request.comment(), request.reason(), request.targetNode())));
    }
}
