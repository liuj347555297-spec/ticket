package cn.servicehub.workflow.infrastructure;

import cn.servicehub.workflow.domain.ControlledJumpRequest;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryTicketWorkflowRepository implements TicketWorkflowRepository {
    private final ConcurrentHashMap<String, WorkflowInstance> instances = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkflowTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<String>> coHandlers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<WorkflowComment> comments = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ControlledJumpRequest> jumpRequests = new CopyOnWriteArrayList<>();

    @Override public void create(WorkflowInstance instance, WorkflowTask initialTask) {
        if (instances.putIfAbsent(instance.ticketId(), instance) != null) throw new IllegalStateException("Workflow already exists");
        tasks.put(initialTask.id(), initialTask);
    }
    @Override public Optional<WorkflowInstance> findInstance(String ticketId) { return Optional.ofNullable(instances.get(ticketId)); }
    @Override public boolean updateInstance(WorkflowInstance replacement, long expectedVersion) {
        AtomicBoolean updated = new AtomicBoolean(false);
        instances.computeIfPresent(replacement.ticketId(), (ignored, current) -> {
            if (current.version() != expectedVersion) return current;
            updated.set(true); return replacement;
        });
        return updated.get();
    }
    @Override public Optional<WorkflowTask> findOpenTask(String ticketId, String nodeKey) {
        return tasks.values().stream().filter(task -> task.ticketId().equals(ticketId) && task.nodeKey().equals(nodeKey)
                && (task.status() == WorkflowTaskStatus.OPEN || task.status() == WorkflowTaskStatus.CLAIMED))
            .findFirst();
    }
    @Override public void saveTask(WorkflowTask task) { tasks.put(task.id(), task); }
    @Override public List<WorkflowTask> findTasks(String ticketId) { return tasks.values().stream().filter(t -> t.ticketId().equals(ticketId)).sorted(Comparator.comparing(WorkflowTask::createdAt)).toList(); }
    @Override public void addCoHandler(String ticketId, String iamUserId, Instant at) { coHandlers.computeIfAbsent(ticketId, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(iamUserId); }
    @Override public boolean hasCoHandler(String ticketId, String iamUserId) { return coHandlers.getOrDefault(ticketId, new CopyOnWriteArrayList<>()).contains(iamUserId); }
    @Override public void addComment(WorkflowComment comment) { comments.add(comment); }
    @Override public List<WorkflowComment> findComments(String ticketId) { return comments.stream().filter(c -> c.ticketId().equals(ticketId)).sorted(Comparator.comparing(WorkflowComment::createdAt)).toList(); }
    @Override public void addJumpRequest(ControlledJumpRequest request) { jumpRequests.add(request); }
    @Override public void appendEvent(String ticketId, String action, String actorIamUserId, String requestId, java.util.Map<String, String> attributes, Instant occurredAt) { /* test adapter retains no audit history */ }
}
