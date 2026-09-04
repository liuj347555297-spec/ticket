package cn.servicehub.workflow.engine;

import java.util.Map;
import java.util.List;
import java.util.Set;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Gateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Real Flowable 7 adapter. Task definition keys are the only accepted lifecycle node identifiers. */
@Component
public class FlowableWorkflowEngineAdapter implements WorkflowEnginePort, cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalEnginePort {
    private static final String PROCESS_KEY = "servicehubTicketLifecycle";
    private static final String DIRECT_ACCEPT_PROCESS_KEY = "servicehubTicketLifecycleDirectAccept";
    private static final String CONTROLLED_JUMP_APPROVAL_PROCESS_KEY = "servicehubControlledJumpApproval";
    private static final String HANDOVER_CONFIRMATION_PROCESS_KEY = "servicehubHandoverConfirmation";
    private static final String COHANDLER_CONFIRMATION_PROCESS_KEY = "servicehubCoHandlerConfirmation";
    private static final String LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY = "servicehubLifecycleActionApproval";
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final org.flowable.engine.HistoryService historyService;
    private final Object lifecycleDeploymentLock = new Object();
    private final boolean directAcceptRouting;
    private final Object approvalDeploymentLock = new Object();
    private final Object handoverDeploymentLock = new Object();
    private final Object coHandlerDeploymentLock = new Object();
    private final Object lifecycleActionApprovalDeploymentLock = new Object();

    public FlowableWorkflowEngineAdapter(RuntimeService runtimeService, TaskService taskService, RepositoryService repositoryService, org.flowable.engine.HistoryService historyService,
                                         @Value("${servicehub.workflow.direct-accept-routing:false}") boolean directAcceptRouting) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.historyService = historyService;
        this.directAcceptRouting = directAcceptRouting;
    }

    @Override
    public WorkflowEngineInstance start(String ticketId) {
        ProcessDefinition definition = resolveLifecycleDefinition();
        // Start by the resolved immutable id.  Starting only by key would be correct in the
        // engine, but would make the application-side version snapshot depend on a second query.
        var instance = runtimeService.startProcessInstanceById(definition.getId(), ticketId, Map.of("ticketId", ticketId));
        return toEngineInstance(instance.getId(), definition.getId());
    }

    @Override
    public WorkflowEngineInstance advance(String instanceId, String expectedNodeKey) {
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
        if (task == null || !expectedNodeKey.equals(task.getTaskDefinitionKey())) {
            throw new IllegalStateException("Workflow node does not permit this action");
        }
        String processDefinitionId = task.getProcessDefinitionId();
        taskService.complete(task.getId());
        return toEngineInstance(instanceId, processDefinitionId);
    }

    @Override
    public void cancel(String instanceId, String reason) {
        runtimeService.deleteProcessInstance(instanceId, reason);
    }

    @Override public WorkflowApprovalDefinition resolveControlledJumpApprovalDefinition() {
        ensureApprovalDefinition();
        var definition = repositoryService.createProcessDefinitionQuery().processDefinitionKey(CONTROLLED_JUMP_APPROVAL_PROCESS_KEY)
            .latestVersion().singleResult();
        if (definition == null) throw new IllegalStateException("Controlled-jump approval definition is unavailable");
        return new WorkflowApprovalDefinition(definition.getKey(), definition.getId(), definition.getVersion());
    }

    @Override public WorkflowEngineInstance startControlledJumpApproval(String approvalRequestId, String ticketId, String applicantIamUserId,
                                                                         WorkflowApprovalDefinition definition, Set<String> candidateIamUserIds, String decisionMode) {
        if (!CONTROLLED_JUMP_APPROVAL_PROCESS_KEY.equals(definition.processKey()) || candidateIamUserIds == null || candidateIamUserIds.isEmpty()
            || !Set.of("ANY_ONE", "ALL_OF").contains(decisionMode)) throw new IllegalArgumentException("Approval definition is not allowed");
        var instance = runtimeService.startProcessInstanceById(definition.processDefinitionId(), approvalRequestId,
            Map.of("approvalRequestId", approvalRequestId, "ticketId", ticketId, "applicantIamUserId", applicantIamUserId,
                "approvalCandidates", List.copyOf(candidateIamUserIds), "approvalDecisionMode", decisionMode));
        return toEngineInstance(instance.getId(), definition.processDefinitionId());
    }

    @Override public WorkflowApprovalDecisionResult decideControlledJumpApproval(String approvalEngineInstanceId, String approverIamUserId, String decision) {
        if (!Set.of("APPROVED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Approval decision is invalid");
        Task task = taskService.createTaskQuery().processInstanceId(approvalEngineInstanceId).taskAssignee(approverIamUserId).singleResult();
        if (task == null || !"approval_decision".equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Approval task is unavailable");
        runtimeService.setVariable(approvalEngineInstanceId, "approvalOutcome", decision);
        taskService.setVariableLocal(task.getId(), "approvalDecision", decision);
        taskService.complete(task.getId());
        boolean completed = runtimeService.createProcessInstanceQuery().processInstanceId(approvalEngineInstanceId).singleResult() == null;
        return new WorkflowApprovalDecisionResult(task.getId(), completed, completed ? decision : null);
    }

    @Override public WorkflowApprovalDefinition resolveHandoverConfirmationDefinition() {
        ensureHandoverDefinition();
        ProcessDefinition definition = latestDefinition(HANDOVER_CONFIRMATION_PROCESS_KEY);
        if (definition == null) throw new IllegalStateException("Handover confirmation definition is unavailable");
        return new WorkflowApprovalDefinition(definition.getKey(), definition.getId(), definition.getVersion());
    }

    @Override public cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalDefinition resolveDefinition() {
        ensureLifecycleActionApprovalDefinition();
        ProcessDefinition definition = latestDefinition(LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY);
        if (definition == null) throw new IllegalStateException("Lifecycle action approval definition is unavailable");
        return new cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalDefinition(definition.getKey(), definition.getId(), definition.getVersion());
    }

    @Override public cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalInstance start(String requestId, String ticketId,
            String applicantIamUserId, cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalDefinition definition, Set<String> candidateIamUserIds, String decisionMode, int requiredApprovalCount) {
        if (!LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY.equals(definition.processKey()) || candidateIamUserIds == null || candidateIamUserIds.isEmpty()
            || !Set.of("ANY_ONE", "ALL_OF", "QUORUM").contains(decisionMode) || requiredApprovalCount < 1 || requiredApprovalCount > candidateIamUserIds.size()) throw new IllegalArgumentException("Lifecycle approval definition is not allowed");
        var instance = runtimeService.startProcessInstanceById(definition.processDefinitionId(), requestId, Map.of("approvalRequestId", requestId,
            "ticketId", ticketId, "applicantIamUserId", applicantIamUserId, "approvalCandidates", List.copyOf(candidateIamUserIds),
            "approvalDecisionMode", decisionMode, "requiredApprovalCount", requiredApprovalCount, "approvalApprovedCount", 0, "approvalOutcome", "PENDING"));
        return new cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalInstance(instance.getId());
    }

    @Override public cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalDecision decide(String instanceId, String approverIamUserId, String decision) {
        if (!Set.of("APPROVED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Approval decision is invalid");
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).taskAssignee(approverIamUserId).singleResult();
        if (task == null || !"lifecycle_action_approval_decision".equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Lifecycle approval task is unavailable");
        int total = taskService.createTaskQuery().processInstanceId(instanceId).taskDefinitionKey("lifecycle_action_approval_decision").count() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) taskService.createTaskQuery().processInstanceId(instanceId).taskDefinitionKey("lifecycle_action_approval_decision").count();
        int approved = runtimeService.getVariable(instanceId, "approvalApprovedCount") instanceof Number value ? value.intValue() : 0;
        String mode = String.valueOf(runtimeService.getVariable(instanceId, "approvalDecisionMode"));
        int required = runtimeService.getVariable(instanceId, "requiredApprovalCount") instanceof Number value ? value.intValue() : 1;
        if ("APPROVED".equals(decision)) approved++;
        int remainingAfter = Math.max(0, total - 1);
        String outcome = switch (mode) {
            case "ANY_ONE" -> decision;
            case "ALL_OF" -> "REJECTED".equals(decision) ? "REJECTED" : (approved >= required ? "APPROVED" : "PENDING");
            case "QUORUM" -> approved >= required ? "APPROVED" : (approved + remainingAfter < required ? "REJECTED" : "PENDING");
            default -> throw new IllegalStateException("Lifecycle approval mode is invalid");
        };
        runtimeService.setVariable(instanceId, "approvalApprovedCount", approved);
        runtimeService.setVariable(instanceId, "approvalOutcome", outcome);
        taskService.setVariableLocal(task.getId(), "approvalDecision", decision);
        taskService.complete(task.getId());
        boolean done = runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() == null;
        return new cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalDecision(task.getId(), done, done ? outcome : null);
    }

    @Override public void cancelExpired(String instanceId) {
        if (runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() != null) runtimeService.deleteProcessInstance(instanceId, "lifecycle-approval-expired");
    }

    @Override public WorkflowEngineInstance startHandoverConfirmation(String handoverRequestId, String ticketId, String applicantIamUserId,
                                                                       String targetIamUserId, WorkflowApprovalDefinition definition) {
        if (!HANDOVER_CONFIRMATION_PROCESS_KEY.equals(definition.processKey()) || targetIamUserId == null || targetIamUserId.isBlank()) {
            throw new IllegalArgumentException("Handover definition is not allowed");
        }
        var instance = runtimeService.startProcessInstanceById(definition.processDefinitionId(), handoverRequestId,
            Map.of("handoverRequestId", handoverRequestId, "ticketId", ticketId, "applicantIamUserId", applicantIamUserId,
                "handoverTarget", targetIamUserId));
        return toEngineInstance(instance.getId(), definition.processDefinitionId());
    }

    @Override public WorkflowApprovalDecisionResult decideHandoverConfirmation(String handoverEngineInstanceId, String targetIamUserId, String decision) {
        if (!Set.of("ACCEPTED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Handover decision is invalid");
        Task task = taskService.createTaskQuery().processInstanceId(handoverEngineInstanceId).taskAssignee(targetIamUserId).singleResult();
        if (task == null || !"handover_decision".equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Handover task is unavailable");
        taskService.setVariableLocal(task.getId(), "handoverOutcome", decision);
        taskService.complete(task.getId());
        boolean completed = runtimeService.createProcessInstanceQuery().processInstanceId(handoverEngineInstanceId).singleResult() == null;
        return new WorkflowApprovalDecisionResult(task.getId(), completed, completed ? ("ACCEPTED".equals(decision) ? "APPROVED" : "REJECTED") : null);
    }

    @Override public WorkflowApprovalDefinition resolveCoHandlerConfirmationDefinition() {
        ensureCoHandlerDefinition();
        ProcessDefinition definition = latestDefinition(COHANDLER_CONFIRMATION_PROCESS_KEY);
        if (definition == null) throw new IllegalStateException("Co-handler confirmation definition is unavailable");
        return new WorkflowApprovalDefinition(definition.getKey(), definition.getId(), definition.getVersion());
    }

    @Override public WorkflowEngineInstance startCoHandlerConfirmation(String requestId, String ticketId, String applicantIamUserId,
                                                                        String targetIamUserId, WorkflowApprovalDefinition definition) {
        if (!COHANDLER_CONFIRMATION_PROCESS_KEY.equals(definition.processKey()) || targetIamUserId == null || targetIamUserId.isBlank()) {
            throw new IllegalArgumentException("Co-handler confirmation definition is not allowed");
        }
        var instance = runtimeService.startProcessInstanceById(definition.processDefinitionId(), requestId,
            Map.of("coHandlerRequestId", requestId, "ticketId", ticketId, "applicantIamUserId", applicantIamUserId,
                "coHandlerTarget", targetIamUserId));
        return toEngineInstance(instance.getId(), definition.processDefinitionId());
    }

    @Override public WorkflowApprovalDecisionResult decideCoHandlerConfirmation(String instanceId, String targetIamUserId, String decision) {
        if (!Set.of("ACCEPTED", "REJECTED").contains(decision)) throw new IllegalArgumentException("Co-handler decision is invalid");
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).taskAssignee(targetIamUserId).singleResult();
        if (task == null || !"cohandler_decision".equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Co-handler confirmation task is unavailable");
        taskService.setVariableLocal(task.getId(), "coHandlerOutcome", decision);
        taskService.complete(task.getId());
        boolean completed = runtimeService.createProcessInstanceQuery().processInstanceId(instanceId).singleResult() == null;
        return new WorkflowApprovalDecisionResult(task.getId(), completed, completed ? ("ACCEPTED".equals(decision) ? "APPROVED" : "REJECTED") : null);
    }

    @Override public List<WorkflowApprovalTask> findPendingControlledJumpApprovalTasks(String iamUserId, int offset, int limit) {
        if (iamUserId == null || iamUserId.isBlank() || limit < 1) return List.of();
        return taskService.createTaskQuery().taskDefinitionKey("approval_decision").taskAssignee(iamUserId)
            .active().orderByTaskCreateTime().asc().listPage(offset, limit).stream()
            .map(task -> {
                // Multi-instance executions carry an execution-local scope. TaskService resolves
                // inherited process variables through that scope, unlike a root-runtime lookup.
                Object requestId = taskService.getVariable(task.getId(), "approvalRequestId");
                return requestId instanceof String value && !value.isBlank()
                    ? new WorkflowApprovalTask(task.getId(), value, task.getCreateTime().toInstant()) : null;
            })
            .filter(java.util.Objects::nonNull).toList();
    }

    @Override public List<WorkflowInboxTask> findPendingInboxTasks(String iamUserId, int offset, int limit) {
        if (iamUserId == null || iamUserId.isBlank() || limit < 1) return List.of();
        return taskService.createTaskQuery().taskAssignee(iamUserId).active().orderByTaskCreateTime().asc()
            .listPage(offset, limit).stream().map(task -> {
                String key = task.getProcessDefinitionId() == null ? null : task.getProcessDefinitionId().split(":")[0];
                if (!Set.of(CONTROLLED_JUMP_APPROVAL_PROCESS_KEY, LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY,
                    HANDOVER_CONFIRMATION_PROCESS_KEY, COHANDLER_CONFIRMATION_PROCESS_KEY).contains(key)) return null;
                String variable = switch (key) {
                    case HANDOVER_CONFIRMATION_PROCESS_KEY -> "handoverRequestId";
                    case COHANDLER_CONFIRMATION_PROCESS_KEY -> "coHandlerRequestId";
                    default -> "approvalRequestId";
                };
                Object request = taskService.getVariable(task.getId(), variable);
                Object ticket = taskService.getVariable(task.getId(), "ticketId");
                return request instanceof String requestId && !requestId.isBlank() && ticket instanceof String ticketId && !ticketId.isBlank()
                    ? new WorkflowInboxTask(task.getId(), key, requestId, ticketId, task.getCreateTime().toInstant()) : null;
            }).filter(java.util.Objects::nonNull).toList();
    }

    @Override public List<WorkflowProcessDefinition> findPublishedDefinitions() {
        return repositoryService.createProcessDefinitionQuery().latestVersion().list().stream()
            .filter(definition -> PROCESS_KEY.equals(definition.getKey()) || DIRECT_ACCEPT_PROCESS_KEY.equals(definition.getKey()) || CONTROLLED_JUMP_APPROVAL_PROCESS_KEY.equals(definition.getKey()) || HANDOVER_CONFIRMATION_PROCESS_KEY.equals(definition.getKey()) || COHANDLER_CONFIRMATION_PROCESS_KEY.equals(definition.getKey()) || LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY.equals(definition.getKey()))
            .sorted(java.util.Comparator.comparing(org.flowable.engine.repository.ProcessDefinition::getKey))
            .map(definition -> new WorkflowProcessDefinition(definition.getKey(), definition.getId(), definition.getName(), definition.getVersion(),
                definition.getDeploymentId(), definition.getDeploymentId() == null ? null : repositoryService.createDeploymentQuery()
                    .deploymentId(definition.getDeploymentId()).singleResult().getDeploymentTime().toInstant()))
            .toList();
    }

    @Override public WorkflowDiagramPreview ticketLifecyclePreview() {
        ProcessDefinition definition = resolveLifecycleDefinition();
        BpmnModel model = repositoryService.getBpmnModel(definition.getId());
        if (model == null || model.getMainProcess() == null) throw new IllegalStateException("Ticket lifecycle diagram is unavailable");
        List<WorkflowDiagramNode> nodes = model.getMainProcess().getFlowElements().stream()
            .filter(FlowNode.class::isInstance).map(FlowNode.class::cast)
            .map(node -> new WorkflowDiagramNode(node.getId(), displayName(node), nodeType(node))).toList();
        Set<String> nodeIds = nodes.stream().map(WorkflowDiagramNode::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<WorkflowDiagramFlow> flows = model.getMainProcess().getFlowElements().stream()
            .filter(SequenceFlow.class::isInstance).map(SequenceFlow.class::cast)
            .filter(flow -> nodeIds.contains(flow.getSourceRef()) && nodeIds.contains(flow.getTargetRef()))
            .map(flow -> new WorkflowDiagramFlow(flow.getId(), flow.getSourceRef(), flow.getTargetRef())).toList();
        return new WorkflowDiagramPreview(definition.getKey(), definition.getId(), definition.getName(), definition.getVersion(), nodes, flows);
    }

    @Override public WorkflowBpmnDiagram ticketLifecycleDiagram() {
        String key = directAcceptRouting ? DIRECT_ACCEPT_PROCESS_KEY : PROCESS_KEY;
        ProcessDefinition definition = latestDefinition(key);
        // Read-only: unlike lifecycle startup, a preview must never deploy or create a new version.
        return definition == null ? WorkflowBpmnDiagram.legacy() : diagram(definition, null);
    }

    @Override public WorkflowBpmnDiagram instanceDiagram(String definitionId, Integer version, String instanceId) {
        if (definitionId == null || version == null) return WorkflowBpmnDiagram.legacy();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(definitionId).singleResult();
        if (definition == null || definition.getVersion() != version || !Set.of(PROCESS_KEY, DIRECT_ACCEPT_PROCESS_KEY).contains(definition.getKey())) return WorkflowBpmnDiagram.legacy();
        return diagram(definition, instanceId);
    }

    private WorkflowBpmnDiagram diagram(ProcessDefinition definition, String instanceId) {
        try (var source = repositoryService.getProcessModel(definition.getId())) {
            byte[] bytes = source.readNBytes(SafeBpmnXml.MAX_XML + 1);
            if (bytes.length > SafeBpmnXml.MAX_XML) throw new IllegalStateException("Diagram is too large");
            var projection = SafeBpmnXml.project(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), definition.getKey());
            List<String> active = instanceId == null ? List.of() : taskService.createTaskQuery().processInstanceId(instanceId).list().stream()
                .filter(t -> definition.getId().equals(t.getProcessDefinitionId())).map(Task::getTaskDefinitionKey).distinct().toList();
            List<String> completed = instanceId == null ? List.of() : historyService.createHistoricActivityInstanceQuery().processInstanceId(instanceId).finished().list().stream()
                .filter(a -> definition.getId().equals(a.getProcessDefinitionId()))
                .filter(a -> a.getDeleteReason() == null)
                .filter(a -> Set.of("startEvent", "endEvent", "userTask", "exclusiveGateway", "parallelGateway", "inclusiveGateway").contains(a.getActivityType()))
                .map(org.flowable.engine.history.HistoricActivityInstance::getActivityId).distinct().toList();
            return new WorkflowBpmnDiagram(definition.getKey(), definition.getId(), definition.getVersion(), projection.xml(), projection.layoutSource(), active, completed, "AVAILABLE");
        } catch (java.io.IOException e) { throw new IllegalStateException("Workflow diagram is unavailable", e); }
    }

    @Override public WorkflowEngineInstance moveControlledActivity(String lifecycleInstanceId, String expectedSourceNode, String targetNode) {
        Task task = taskService.createTaskQuery().processInstanceId(lifecycleInstanceId).singleResult();
        if (task == null || !expectedSourceNode.equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Lifecycle source activity has changed");
        String processDefinitionId = task.getProcessDefinitionId();
        runtimeService.createChangeActivityStateBuilder().processInstanceId(lifecycleInstanceId)
            .moveActivityIdTo(expectedSourceNode, targetNode).changeState();
        return toEngineInstance(lifecycleInstanceId, processDefinitionId);
    }

    private ProcessDefinition resolveLifecycleDefinition() {
        synchronized (lifecycleDeploymentLock) {
            String key = directAcceptRouting ? DIRECT_ACCEPT_PROCESS_KEY : PROCESS_KEY;
            ProcessDefinition definition = latestDefinition(key);
            if (definition != null) return definition;
            // Duplicate filtering makes an idempotent controlled application deployment. The
            // local lock also prevents duplicate versions during simultaneous first requests.
            repositoryService.createDeployment().name(directAcceptRouting ? "servicehub-ticket-lifecycle-direct-accept" : "servicehub-ticket-lifecycle")
                .enableDuplicateFiltering().addClasspathResource(directAcceptRouting ? "processes/ticket-lifecycle-direct-accept.bpmn20.xml" : "processes/ticket-lifecycle.bpmn20.xml").deploy();
            definition = latestDefinition(key);
            if (definition == null) throw new IllegalStateException("Ticket lifecycle definition is unavailable");
            return definition;
        }
    }

    private void ensureApprovalDefinition() {
        synchronized (approvalDeploymentLock) {
            if (latestDefinition(CONTROLLED_JUMP_APPROVAL_PROCESS_KEY) == null) {
                repositoryService.createDeployment().name("servicehub-controlled-jump-approval")
                    .enableDuplicateFiltering().addClasspathResource("processes/controlled-jump-approval.bpmn20.xml").deploy();
            }
        }
    }

    private void ensureHandoverDefinition() {
        synchronized (handoverDeploymentLock) {
            if (latestDefinition(HANDOVER_CONFIRMATION_PROCESS_KEY) == null) {
                repositoryService.createDeployment().name("servicehub-handover-confirmation")
                    .enableDuplicateFiltering().addClasspathResource("processes/handover-confirmation.bpmn20.xml").deploy();
            }
        }
    }

    private void ensureCoHandlerDefinition() {
        synchronized (coHandlerDeploymentLock) {
            if (latestDefinition(COHANDLER_CONFIRMATION_PROCESS_KEY) == null) {
                repositoryService.createDeployment().name("servicehub-cohandler-confirmation")
                    .enableDuplicateFiltering().addClasspathResource("processes/cohandler-confirmation.bpmn20.xml").deploy();
            }
        }
    }

    private void ensureLifecycleActionApprovalDefinition() {
        synchronized (lifecycleActionApprovalDeploymentLock) {
            if (latestDefinition(LIFECYCLE_ACTION_APPROVAL_PROCESS_KEY) == null) {
                repositoryService.createDeployment().name("servicehub-lifecycle-action-approval")
                    .enableDuplicateFiltering().addClasspathResource("processes/lifecycle-action-approval.bpmn20.xml").deploy();
            }
        }
    }

    private ProcessDefinition latestDefinition(String processKey) {
        return repositoryService.createProcessDefinitionQuery().processDefinitionKey(processKey).latestVersion().singleResult();
    }

    private static String displayName(FlowNode node) {
        if (node.getName() != null && !node.getName().isBlank()) return node.getName().trim();
        if (node instanceof StartEvent) return "开始";
        if (node instanceof EndEvent) return "结束";
        return node.getId();
    }

    private static String nodeType(FlowNode node) {
        if (node instanceof StartEvent) return "START";
        if (node instanceof EndEvent) return "END";
        if (node instanceof UserTask) return "USER_TASK";
        if (node instanceof Gateway) return "GATEWAY";
        return "ACTIVITY";
    }

    private WorkflowEngineInstance toEngineInstance(String instanceId, String processDefinitionId) {
        // A multi-instance approval deliberately has more than one live task.  This summary is
        // not an authorization decision and therefore uses a representative task only.
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).orderByTaskCreateTime().asc().listPage(0, 1).stream().findFirst().orElse(null);
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(processDefinitionId).singleResult();
        if (definition == null) throw new IllegalStateException("Workflow process definition is unavailable");
        return new WorkflowEngineInstance(instanceId, task == null ? "completed" : task.getTaskDefinitionKey(), task == null ? null : task.getId(),
            definition.getId(), definition.getVersion());
    }
}
