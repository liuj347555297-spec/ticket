package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.SlaPolicy;
import cn.servicehub.sla.domain.SlaPolicyRepository;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlSlaPolicyRepository implements SlaPolicyRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlSlaPolicyRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public List<SlaPolicy> findAll() { return jdbc.query("SELECT * FROM sla_policy ORDER BY policy_name ASC", (rs, n) -> map(rs)); }
    public List<SlaPolicy> findActive() { return jdbc.query("SELECT * FROM sla_policy WHERE active = TRUE ORDER BY policy_name ASC", (rs, n) -> map(rs)); }
    public Optional<SlaPolicy> findById(String id) { return jdbc.query("SELECT * FROM sla_policy WHERE id = ?", (rs, n) -> map(rs), id).stream().findFirst(); }
    public SlaPolicy save(SlaPolicy policy, Long expectedVersion) {
        if (expectedVersion == null) jdbc.update("INSERT INTO sla_policy (id, policy_name, service_catalog_item_id, priority, organization_scope_id, response_target_minutes, resolution_target_minutes, calendar_key, pause_statuses, active, version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", policy.id(), policy.name(), policy.serviceCatalogItemId(), policy.priority() == null ? null : policy.priority().name(), policy.organizationScopeId(), policy.responseTargetMinutes(), policy.resolutionTargetMinutes(), policy.calendarKey(), asJson(policy.pauseStatuses()), policy.active(), policy.version(), Timestamp.from(policy.createdAt()), Timestamp.from(policy.updatedAt()));
        else { int changed = jdbc.update("UPDATE sla_policy SET policy_name=?, service_catalog_item_id=?, priority=?, organization_scope_id=?, response_target_minutes=?, resolution_target_minutes=?, calendar_key=?, pause_statuses=?, active=?, version=?, updated_at=? WHERE id=? AND version=?", policy.name(), policy.serviceCatalogItemId(), policy.priority() == null ? null : policy.priority().name(), policy.organizationScopeId(), policy.responseTargetMinutes(), policy.resolutionTargetMinutes(), policy.calendarKey(), asJson(policy.pauseStatuses()), policy.active(), policy.version(), Timestamp.from(policy.updatedAt()), policy.id(), expectedVersion); if (changed != 1) throw new IllegalStateException("SLA policy version conflict"); }
        return policy;
    }
    private SlaPolicy map(java.sql.ResultSet rs) throws java.sql.SQLException { try { Set<TicketStatus> pause = json.readValue(rs.getString("pause_statuses"), new TypeReference<Set<TicketStatus>>() {}); return new SlaPolicy(rs.getString("id"), rs.getString("policy_name"), rs.getString("service_catalog_item_id"), rs.getString("priority") == null ? null : TicketPriority.valueOf(rs.getString("priority")), rs.getString("organization_scope_id"), rs.getInt("response_target_minutes"), rs.getInt("resolution_target_minutes"), rs.getString("calendar_key"), pause, rs.getBoolean("active"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()); } catch (Exception e) { throw new java.sql.SQLException("Unable to map SLA policy", e); } }
    private String asJson(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("SLA JSON cannot be persisted", e); } }
}
