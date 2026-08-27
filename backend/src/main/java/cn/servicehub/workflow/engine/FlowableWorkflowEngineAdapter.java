package cn.servicehub.workflow.engine;

import java.util.Map;
import java.util.List;
import java.util.Set;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/** Real Flowable 7 adapter. Task definition keys are the only accepted lifecycle node identifiers. */
@Component
public class FlowableWorkflowEngineAdapter implements WorkflowEnginePort {
    private static final String PROCESS_KEY = "servicehubTicketLifecycle";
    private static final String CONTROLLED_JUMP_APPROVAL_PROCESS_KEY = "servicehubControlledJumpApproval";
    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;

    public FlowableWorkflowEngineAdapter(RuntimeService runtimeService, TaskService taskService, RepositoryService repositoryService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.repositoryService = repositoryService;
    }

    @Override
    public WorkflowEngineInstance start(String ticketId) {
        if (repositoryService.createProcessDefinitionQuery().processDefinitionKey(PROCESS_KEY).latestVersion().singleResult() == null) {
            // Explicit deployment keeps the lifecycle available even when an operator disables
            // Flowable's broad classpath autodeployer for production hardening.
            repositoryService.createDeployment().name("servicehub-ticket-lifecycle")
                .addClasspathResource("processes/ticket-lifecycle.bpmn20.xml").deploy();
        }
        var instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, ticketId, Map.of("ticketId", ticketId));
        return toEngineInstance(instance.getId());
    }

    @Override
    public WorkflowEngineInstance advance(String instanceId, String expectedNodeKey) {
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
        if (task == null || !expectedNodeKey.equals(task.getTaskDefinitionKey())) {
            throw new IllegalStateException("Workflow node does not permit this action");
        }
        taskService.complete(task.getId());
        return toEngineInstance(instanceId);
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
        return toEngineInstance(instance.getId());
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

    @Override public List<WorkflowProcessDefinition> findPublishedDefinitions() {
        return repositoryService.createProcessDefinitionQuery().latestVersion().list().stream()
            .filter(definition -> PROCESS_KEY.equals(definition.getKey()) || CONTROLLED_JUMP_APPROVAL_PROCESS_KEY.equals(definition.getKey()))
            .sorted(java.util.Comparator.comparing(org.flowable.engine.repository.ProcessDefinition::getKey))
            .map(definition -> new WorkflowProcessDefinition(definition.getKey(), definition.getId(), definition.getName(), definition.getVersion(),
                definition.getDeploymentId(), definition.getDeploymentId() == null ? null : repositoryService.createDeploymentQuery()
                    .deploymentId(definition.getDeploymentId()).singleResult().getDeploymentTime().toInstant()))
            .toList();
    }

    @Override public WorkflowEngineInstance moveControlledActivity(String lifecycleInstanceId, String expectedSourceNode, String targetNode) {
        Task task = taskService.createTaskQuery().processInstanceId(lifecycleInstanceId).singleResult();
        if (task == null || !expectedSourceNode.equals(task.getTaskDefinitionKey())) throw new IllegalStateException("Lifecycle source activity has changed");
        runtimeService.createChangeActivityStateBuilder().processInstanceId(lifecycleInstanceId)
            .moveActivityIdTo(expectedSourceNode, targetNode).changeState();
        return toEngineInstance(lifecycleInstanceId);
    }

    private void ensureApprovalDefinition() {
        if (repositoryService.createProcessDefinitionQuery().processDefinitionKey(CONTROLLED_JUMP_APPROVAL_PROCESS_KEY).latestVersion().singleResult() == null) {
            repositoryService.createDeployment().name("servicehub-controlled-jump-approval")
                .addClasspathResource("processes/controlled-jump-approval.bpmn20.xml").deploy();
        }
    }

    private WorkflowEngineInstance toEngineInstance(String instanceId) {
        // A multi-instance approval deliberately has more than one live task.  This summary is
        // not an authorization decision and therefore uses a representative task only.
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).orderByTaskCreateTime().asc().listPage(0, 1).stream().findFirst().orElse(null);
        return new WorkflowEngineInstance(instanceId, task == null ? "completed" : task.getTaskDefinitionKey(), task == null ? null : task.getId());
    }
}
