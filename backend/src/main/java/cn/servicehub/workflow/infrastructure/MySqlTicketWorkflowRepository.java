package cn.servicehub.workflow.infrastructure;

import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.workflow.domain.CollaborationRole;
import cn.servicehub.workflow.domain.ControlledJumpRequest;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowComment;
import cn.servicehub.workflow.domain.WorkflowInstance;
import cn.servicehub.workflow.domain.WorkflowTask;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlTicketWorkflowRepository implements TicketWorkflowRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public MySqlTicketWorkflowRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) { this.jdbc = jdbc; this.objectMapper = objectMapper; }
    @Override public void create(WorkflowInstance i, WorkflowTask t) { jdbc.update("INSERT INTO ticket_workflow_instance (ticket_id, engine_instance_id, current_node, status, resume_status, escalation_level, primary_assignee_iam_user_id, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", i.ticketId(), i.engineInstanceId(), i.currentNode(), i.status().name(), name(i.resumeStatus()), i.escalationLevel(), i.primaryAssigneeIamUserId(), i.version(), ts(i.createdAt()), ts(i.updatedAt())); saveTask(t); }
    @Override public Optional<WorkflowInstance> findInstance(String ticketId) { return jdbc.query("SELECT * FROM ticket_workflow_instance WHERE ticket_id = ?", (rs, n) -> new WorkflowInstance(rs.getString("ticket_id"), rs.getString("engine_instance_id"), rs.getString("current_node"), TicketStatus.valueOf(rs.getString("status")), enumOrNull(rs.getString("resume_status"), TicketStatus.class), rs.getInt("escalation_level"), rs.getString("primary_assignee_iam_user_id"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), ticketId).stream().findFirst(); }
    @Override public boolean updateInstance(WorkflowInstance i, long expected) { return jdbc.update("UPDATE ticket_workflow_instance SET engine_instance_id=?, current_node=?, status=?, resume_status=?, escalation_level=?, primary_assignee_iam_user_id=?, version=?, updated_at=? WHERE ticket_id=? AND version=?", i.engineInstanceId(), i.currentNode(), i.status().name(), name(i.resumeStatus()), i.escalationLevel(), i.primaryAssigneeIamUserId(), i.version(), ts(i.updatedAt()), i.ticketId(), expected) == 1; }
    @Override public Optional<WorkflowTask> findOpenTask(String ticketId, String node) { return jdbc.query("SELECT * FROM ticket_workflow_task WHERE ticket_id=? AND node_key=? AND status IN ('OPEN','CLAIMED') ORDER BY created_at LIMIT 1", (rs, n) -> task(rs), ticketId, node).stream().findFirst(); }
    @Override public void saveTask(WorkflowTask t) { jdbc.update("INSERT INTO ticket_workflow_task (id, ticket_id, engine_task_id, node_key, status, candidate_role, candidate_iam_user_id, assignee_iam_user_id, collaboration_role, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE engine_task_id=VALUES(engine_task_id), node_key=VALUES(node_key), status=VALUES(status), candidate_role=VALUES(candidate_role), candidate_iam_user_id=VALUES(candidate_iam_user_id), assignee_iam_user_id=VALUES(assignee_iam_user_id), collaboration_role=VALUES(collaboration_role), version=VALUES(version), updated_at=VALUES(updated_at)", t.id(), t.ticketId(), t.engineTaskId(), t.nodeKey(), t.status().name(), t.candidateRole(), t.candidateIamUserId(), t.assigneeIamUserId(), name(t.collaborationRole()), t.version(), ts(t.createdAt()), ts(t.updatedAt())); }
    @Override public List<WorkflowTask> findTasks(String ticketId) { return jdbc.query("SELECT * FROM ticket_workflow_task WHERE ticket_id=? ORDER BY created_at", (rs, n) -> task(rs), ticketId); }
    @Override public void addCoHandler(String ticketId, String user, Instant at) { jdbc.update("INSERT IGNORE INTO ticket_co_handler (ticket_id, iam_user_id, added_at) VALUES (?, ?, ?)", ticketId, user, ts(at)); }
    @Override public boolean hasCoHandler(String ticketId, String user) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ticket_co_handler WHERE ticket_id=? AND iam_user_id=?)", Boolean.class, ticketId, user)); }
    @Override public void addComment(WorkflowComment c) { jdbc.update("INSERT INTO ticket_internal_comment (id, ticket_id, author_iam_user_id, body, created_at) VALUES (?, ?, ?, ?, ?)", c.id(), c.ticketId(), c.authorIamUserId(), c.body(), ts(c.createdAt())); }
    @Override public List<WorkflowComment> findComments(String ticketId) { return jdbc.query("SELECT * FROM ticket_internal_comment WHERE ticket_id=? ORDER BY created_at", (rs, n) -> new WorkflowComment(rs.getString("id"), rs.getString("ticket_id"), rs.getString("author_iam_user_id"), rs.getString("body"), rs.getTimestamp("created_at").toInstant()), ticketId); }
    @Override public void addJumpRequest(ControlledJumpRequest r) { jdbc.update("INSERT INTO ticket_controlled_jump_request (id, ticket_id, applicant_iam_user_id, source_node, target_node, reason, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", r.id(), r.ticketId(), r.applicantIamUserId(), r.sourceNode(), r.targetNode(), r.reason(), r.status(), ts(r.createdAt())); }
    @Override public void appendEvent(String ticketId, String action, String actor, String requestId, java.util.Map<String, String> attributes, Instant occurredAt) { try { jdbc.update("INSERT INTO ticket_workflow_event (ticket_id, action, actor_iam_user_id, request_id, attributes, occurred_at) VALUES (?, ?, ?, ?, ?, ?)", ticketId, action, actor, requestId, objectMapper.writeValueAsString(attributes), ts(occurredAt)); } catch (Exception exception) { throw new IllegalStateException("Workflow audit event cannot be persisted", exception); } }
    private WorkflowTask task(java.sql.ResultSet rs) throws java.sql.SQLException { return new WorkflowTask(rs.getString("id"), rs.getString("ticket_id"), rs.getString("engine_task_id"), rs.getString("node_key"), WorkflowTaskStatus.valueOf(rs.getString("status")), rs.getString("candidate_role"), rs.getString("candidate_iam_user_id"), rs.getString("assignee_iam_user_id"), enumOrNull(rs.getString("collaboration_role"), CollaborationRole.class), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static <T extends Enum<T>> T enumOrNull(String value, Class<T> type) { return value == null ? null : Enum.valueOf(type, value); }
}
