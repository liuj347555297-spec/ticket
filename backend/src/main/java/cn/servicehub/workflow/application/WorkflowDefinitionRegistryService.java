package cn.servicehub.workflow.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.workflow.engine.WorkflowEnginePort;
import cn.servicehub.workflow.engine.WorkflowDiagramPreview;
import cn.servicehub.workflow.engine.WorkflowProcessDefinition;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Platform read model for release verification; runtime BPMN deployment remains part of controlled application delivery. */
@Service
public class WorkflowDefinitionRegistryService {
    private final WorkflowEnginePort engine;
    private final CurrentUserProvider users;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();
    public WorkflowDefinitionRegistryService(WorkflowEnginePort engine, CurrentUserProvider users, AuditEventPublisher audit) {
        this.engine = engine; this.users = users; this.audit = audit;
    }
    public List<WorkflowProcessDefinition> listPublished() {
        CurrentUser actor = users.requireCurrentUser();
        if (!actor.authorities().contains("ROLE_PLATFORM_ADMIN")) throw new AccessDeniedException("Workflow definition registry requires platform-admin authority");
        List<WorkflowProcessDefinition> definitions = engine.findPublishedDefinitions();
        audit.publish(new AuditEvent(clock.instant(), requestId(), actor.iamUserId(), "WORKFLOW_DEFINITION_REGISTRY_READ", "workflow-definition", "collection",
            Map.of("returned", String.valueOf(definitions.size()))));
        return definitions;
    }
    /** Any authenticated requester may inspect the curated lifecycle preview before creating a ticket. */
    public WorkflowDiagramPreview ticketLifecyclePreview() {
        CurrentUser actor = users.requireCurrentUser();
        WorkflowDiagramPreview preview = engine.ticketLifecyclePreview();
        audit.publish(new AuditEvent(clock.instant(), requestId(), actor.iamUserId(), "WORKFLOW_LIFECYCLE_PREVIEW_READ", "workflow-definition",
            preview.processKey(), Map.of("version", String.valueOf(preview.version()), "nodeCount", String.valueOf(preview.nodes().size()))));
        return preview;
    }
    public cn.servicehub.workflow.engine.WorkflowBpmnDiagram ticketLifecycleDiagram() {
        CurrentUser actor = users.requireCurrentUser();
        var diagram = engine.ticketLifecycleDiagram();
        audit.publish(new AuditEvent(clock.instant(), requestId(), actor.iamUserId(), "WORKFLOW_DIAGRAM_READ", "workflow-definition", diagram.processKey(), Map.of("availability", diagram.availability())));
        return diagram;
    }
    private String requestId() { return MDC.get("requestId") == null ? "system" : MDC.get("requestId"); }
}
