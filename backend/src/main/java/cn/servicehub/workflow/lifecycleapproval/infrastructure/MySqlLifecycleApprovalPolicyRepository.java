package cn.servicehub.workflow.lifecycleapproval.infrastructure;

import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlLifecycleApprovalPolicyRepository implements LifecycleApprovalPolicyRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlLifecycleApprovalPolicyRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public List<LifecycleApprovalPolicy> findAll() { return jdbc.query("SELECT * FROM lifecycle_approval_policy ORDER BY updated_at DESC", (rs, n) -> map(rs)); }
    @Override public List<LifecycleApprovalPolicy> findPublishedByAction(String actionCode) { return jdbc.query("SELECT * FROM lifecycle_approval_policy WHERE lifecycle_status='PUBLISHED' AND action_code=?", (rs, n) -> map(rs), actionCode); }
    @Override public Optional<LifecycleApprovalPolicy> findById(String id) { return jdbc.query("SELECT * FROM lifecycle_approval_policy WHERE id=?", (rs, n) -> map(rs), id).stream().findFirst(); }
    @Override public LifecycleApprovalPolicy save(LifecycleApprovalPolicy p, Long expectedVersion) {
        try {
            if (expectedVersion == null) jdbc.update("INSERT INTO lifecycle_approval_policy (id,policy_name,action_code,service_catalog_item_id,ticket_priority,candidate_roles_json,decision_mode,approval_threshold_percent,timeout_minutes,timeout_policy_version,escalation_policy_version,lifecycle_status,version,created_at,updated_at,published_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", p.id(),p.name(),p.action().name(),p.serviceCatalogItemId(),p.priority()==null?null:p.priority().name(),json.writeValueAsString(p.candidateRoles()),p.decisionMode(),p.approvalThresholdPercent(),p.timeoutMinutes(),p.timeoutPolicyVersion(),p.escalationPolicyVersion(),p.status(),p.version(),Timestamp.from(p.createdAt()),Timestamp.from(p.updatedAt()),p.publishedAt()==null?null:Timestamp.from(p.publishedAt()));
            else if (jdbc.update("UPDATE lifecycle_approval_policy SET policy_name=?,action_code=?,service_catalog_item_id=?,ticket_priority=?,candidate_roles_json=?,decision_mode=?,approval_threshold_percent=?,timeout_minutes=?,timeout_policy_version=?,escalation_policy_version=?,lifecycle_status=?,version=?,updated_at=?,published_at=? WHERE id=? AND version=?", p.name(),p.action().name(),p.serviceCatalogItemId(),p.priority()==null?null:p.priority().name(),json.writeValueAsString(p.candidateRoles()),p.decisionMode(),p.approvalThresholdPercent(),p.timeoutMinutes(),p.timeoutPolicyVersion(),p.escalationPolicyVersion(),p.status(),p.version(),Timestamp.from(p.updatedAt()),p.publishedAt()==null?null:Timestamp.from(p.publishedAt()),p.id(),expectedVersion) != 1) throw new IllegalStateException("Lifecycle approval policy version conflict");
            return p;
        } catch (IllegalStateException e) { throw e; } catch (Exception e) { throw new IllegalStateException("Lifecycle approval policy cannot be persisted", e); }
    }
    private LifecycleApprovalPolicy map(java.sql.ResultSet rs) throws java.sql.SQLException { try { return new LifecycleApprovalPolicy(rs.getString("id"),rs.getString("policy_name"),WorkflowAction.valueOf(rs.getString("action_code")),rs.getString("service_catalog_item_id"),rs.getString("ticket_priority")==null?null:TicketPriority.valueOf(rs.getString("ticket_priority")),json.readValue(rs.getString("candidate_roles_json"),new TypeReference<java.util.Set<String>>(){}),rs.getString("decision_mode"),rs.getInt("approval_threshold_percent"),rs.getInt("timeout_minutes"),rs.getString("timeout_policy_version"),rs.getString("escalation_policy_version"),rs.getString("lifecycle_status"),rs.getLong("version"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant()); } catch(Exception e) { throw new java.sql.SQLException("Lifecycle approval policy is unreadable",e); } }
}
