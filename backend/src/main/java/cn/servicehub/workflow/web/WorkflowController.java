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
import org.springframework.web.bind.annotation.RequestParam;
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
    @GetMapping("/diagram")
    cn.servicehub.workflow.engine.WorkflowBpmnDiagram diagram(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId) {
        ticketService.get(ticketId);
        return workflowService.diagram(ticketId);
    }
    @GetMapping("/next-handler-candidates")
    java.util.List<cn.servicehub.workflow.routing.NodeAssignmentResolver.HandlerCandidate> nextHandlerCandidates(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @RequestParam @Pattern(regexp = "^processing$") String targetNode) { ticketService.get(ticketId); return workflowService.nextHandlerCandidates(ticketId, targetNode); }

    @PostMapping("/actions")
    TicketResponse action(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
                          @RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp = "^\"?[0-9]+\"?$") String ifMatch,
                          @Valid @RequestBody WorkflowActionRequest request) {
        long expectedVersion = Long.parseLong(ifMatch.replace("\"", ""));
        return TicketResponse.from(workflowService.act(ticketId, new WorkflowActionCommand(request.action(), expectedVersion,
            request.targetIamUserId(), request.comment(), request.reason(), request.targetNode())));
    }

    @PostMapping("/approval-requests/{requestId}/decisions")
    cn.servicehub.workflow.domain.ControlledJumpRequest decide(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId, @Valid @RequestBody ApprovalDecisionRequest request) {
        return workflowService.decideJumpRequest(ticketId, requestId, request.decision(), request.reason());
    }

    @PostMapping("/handover-requests/{requestId}/decisions")
    cn.servicehub.workflow.domain.HandoverRequest decideHandover(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId, @Valid @RequestBody HandoverDecisionRequest request) {
        return workflowService.decideHandover(ticketId, requestId, request.decision(), request.reason());
    }

    @PostMapping("/cohandler-requests/{requestId}/decisions")
    cn.servicehub.workflow.domain.CoHandlerRequest decideCoHandler(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId, @Valid @RequestBody HandoverDecisionRequest request) {
        return workflowService.decideCoHandler(ticketId, requestId, request.decision(), request.reason());
    }

    @PostMapping("/delegations")
    cn.servicehub.workflow.domain.TicketDelegation delegate(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @Valid @RequestBody DelegationRequest request) {
        return workflowService.createDelegation(ticketId, request.delegateIamUserId(), request.effectiveUntil().toInstant(), request.reason());
    }

    @PostMapping("/lifecycle-approval-requests/{requestId}/decisions")
    cn.servicehub.workflow.application.LifecycleActionApprovalSummary decideLifecycleAction(
        @PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId, @Valid @RequestBody ApprovalDecisionRequest request) {
        return cn.servicehub.workflow.application.LifecycleActionApprovalSummary.from(
            workflowService.decideLifecycleActionApproval(ticketId, requestId, request.decision(), request.reason()));
    }

    @GetMapping("/approval-requests/{requestId}/preflight")
    cn.servicehub.workflow.application.ControlledJumpPreflight preflight(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId,
        @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId) {
        ticketService.get(ticketId);
        return workflowService.preflight(ticketId, requestId);
    }

    @PostMapping("/approval-requests/{requestId}/execute")
    TicketResponse execute(@PathVariable @Pattern(regexp = "^TKT-[0-9]{8}-[0-9]{6}$") String ticketId, @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String requestId,
        @RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp = "^\"?[0-9]+\"?$") String ifMatch) {
        return TicketResponse.from(workflowService.executeApprovedJump(ticketId, requestId, Long.parseLong(ifMatch.replace("\"", ""))));
    }
}
