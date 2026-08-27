package cn.servicehub.workflow.web;

import cn.servicehub.workflow.application.WorkflowDefinitionRegistryService;
import cn.servicehub.workflow.engine.WorkflowProcessDefinition;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A read-only release-verification endpoint; it deliberately has no arbitrary BPMN upload or deployment route. */
@RestController
@RequestMapping("/api/v1/workflow/definitions")
public class WorkflowDefinitionRegistryController {
    private final WorkflowDefinitionRegistryService service;
    public WorkflowDefinitionRegistryController(WorkflowDefinitionRegistryService service) { this.service = service; }
    @GetMapping
    List<WorkflowProcessDefinition> listPublished() { return service.listPublished(); }
}
