package cn.servicehub.workflow.web;

import cn.servicehub.workflow.application.WorkflowDefinitionRegistryService;
import cn.servicehub.workflow.engine.WorkflowDiagramPreview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only, browser-safe Flowable lifecycle preview for a ticket being created. */
@RestController
@RequestMapping("/api/v1/workflow/ticket-lifecycle")
public class TicketLifecyclePreviewController {
    private final WorkflowDefinitionRegistryService definitions;
    public TicketLifecyclePreviewController(WorkflowDefinitionRegistryService definitions) { this.definitions = definitions; }

    @GetMapping("/preview")
    WorkflowDiagramPreview preview() { return definitions.ticketLifecyclePreview(); }
    @GetMapping("/diagram")
    cn.servicehub.workflow.engine.WorkflowBpmnDiagram diagram() { return definitions.ticketLifecycleDiagram(); }
}
