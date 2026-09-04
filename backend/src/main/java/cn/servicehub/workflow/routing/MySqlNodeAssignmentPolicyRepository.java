package cn.servicehub.workflow.routing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlNodeAssignmentPolicyRepository implements NodeAssignmentPolicyRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlNodeAssignmentPolicyRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public Optional<NodeAssignmentPolicy> findActive(String catalog, String node) {
        return jdbc.query("SELECT * FROM workflow_node_assignment_policy WHERE service_catalog_item_id=? AND node_key=? AND enabled=TRUE",
            (rs, n) -> new NodeAssignmentPolicy(rs.getString("service_catalog_item_id"), rs.getString("node_key"),
                NodeAssignmentMode.valueOf(rs.getString("assignment_mode")),rs.getString("queue_code"), roles(rs.getString("candidate_roles_json")), rs.getLong("version"), rs.getBoolean("enabled")), catalog, node).stream().findFirst();
    }
    @Override public List<NodeAssignmentPolicy> findByCatalogItemId(String catalog) {
        return jdbc.query("SELECT * FROM workflow_node_assignment_policy WHERE service_catalog_item_id=? ORDER BY node_key", (rs, n) -> map(rs), catalog);
    }
    @Override public NodeAssignmentPolicy save(NodeAssignmentPolicy value, long expectedVersion, String actor) {
        try {
            int changed = jdbc.update("UPDATE workflow_node_assignment_policy SET assignment_mode=?,queue_code=?,candidate_roles_json=?,enabled=?,version=version+1,updated_by_iam_user_id=?,updated_at=UTC_TIMESTAMP(6) WHERE service_catalog_item_id=? AND node_key=? AND version=?", value.mode().name(),value.queueCode(), json.writeValueAsString(value.candidateRoles()), value.enabled(), actor, value.serviceCatalogItemId(), value.nodeKey(), expectedVersion);
            if (changed == 0 && expectedVersion == 0) jdbc.update("INSERT INTO workflow_node_assignment_policy (service_catalog_item_id,node_key,assignment_mode,queue_code,candidate_roles_json,enabled,version,updated_by_iam_user_id,updated_at) VALUES (?,?,?,?,?,?,1,?,UTC_TIMESTAMP(6))", value.serviceCatalogItemId(), value.nodeKey(), value.mode().name(),value.queueCode(), json.writeValueAsString(value.candidateRoles()), value.enabled(), actor);
            else if (changed == 0) throw new cn.servicehub.workflow.application.WorkflowConflictException();
            return findByCatalogItemId(value.serviceCatalogItemId()).stream().filter(item -> item.nodeKey().equals(value.nodeKey())).findFirst().orElseThrow();
        } catch (cn.servicehub.workflow.application.WorkflowConflictException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Workflow assignment policy cannot be persisted", exception); }
    }
    @Override public List<NodeAssignmentSnapshot> findSnapshots(String ticketId) { return jdbc.query("SELECT * FROM ticket_workflow_node_assignment_snapshot WHERE ticket_id=? ORDER BY captured_at", (rs, n) -> new NodeAssignmentSnapshot(rs.getString("ticket_id"), rs.getString("node_key"), NodeAssignmentMode.valueOf(rs.getString("assignment_mode")),rs.getString("queue_code"), roles(rs.getString("candidate_roles_json")), rs.getLong("policy_version"), rs.getString("selected_iam_user_id"), rs.getTimestamp("captured_at").toInstant()), ticketId); }
    @Override public void saveSnapshot(NodeAssignmentSnapshot value) {
        try { jdbc.update("INSERT INTO ticket_workflow_node_assignment_snapshot (ticket_id,node_key,assignment_mode,queue_code,candidate_roles_json,policy_version,selected_iam_user_id,captured_at) VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE assignment_mode=VALUES(assignment_mode),queue_code=VALUES(queue_code),candidate_roles_json=VALUES(candidate_roles_json),policy_version=VALUES(policy_version),selected_iam_user_id=VALUES(selected_iam_user_id),captured_at=VALUES(captured_at)", value.ticketId(), value.nodeKey(), value.mode().name(),value.queueCode(), json.writeValueAsString(value.candidateRoles()), value.policyVersion(), value.selectedIamUserId(), Timestamp.from(value.capturedAt())); }
        catch (Exception exception) { throw new IllegalStateException("Workflow assignment snapshot cannot be persisted", exception); }
    }
    private Set<String> roles(String raw) { try { return json.readValue(raw, new TypeReference<Set<String>>() { }); } catch (Exception exception) { throw new IllegalStateException("Workflow assignment policy is unreadable", exception); } }
    private NodeAssignmentPolicy map(java.sql.ResultSet rs) throws java.sql.SQLException { return new NodeAssignmentPolicy(rs.getString("service_catalog_item_id"), rs.getString("node_key"), NodeAssignmentMode.valueOf(rs.getString("assignment_mode")),rs.getString("queue_code"), roles(rs.getString("candidate_roles_json")), rs.getLong("version"), rs.getBoolean("enabled")); }
}
