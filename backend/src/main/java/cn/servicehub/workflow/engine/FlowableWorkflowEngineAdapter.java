package cn.servicehub.workflow.engine;

import java.util.Map;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/** Real Flowable 7 adapter. Task definition keys are the only accepted lifecycle node identifiers. */
@Component
public class FlowableWorkflowEngineAdapter implements WorkflowEnginePort {
    private static final String PROCESS_KEY = "servicehubTicketLifecycle";
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

    private WorkflowEngineInstance toEngineInstance(String instanceId) {
        Task task = taskService.createTaskQuery().processInstanceId(instanceId).singleResult();
        return new WorkflowEngineInstance(instanceId, task == null ? "completed" : task.getTaskDefinitionKey(), task == null ? null : task.getId());
    }
}
