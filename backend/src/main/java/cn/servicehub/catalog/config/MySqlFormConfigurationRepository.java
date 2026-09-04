package cn.servicehub.catalog.config;

import cn.servicehub.ticket.domain.TicketType;
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
public class MySqlFormConfigurationRepository implements FormConfigurationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public MySqlFormConfigurationRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    @Override public List<ManagedFormConfiguration> findAll() { return jdbc.query("SELECT * FROM service_catalog_form_configuration ORDER BY id", (rs, n) -> map(rs)); }
    @Override public Optional<ManagedFormConfiguration> findById(String id) { return jdbc.query("SELECT * FROM service_catalog_form_configuration WHERE id=?", (rs,n)->map(rs), id).stream().findFirst(); }
    @Override public List<ManagedFormConfiguration> findPublishedHistory(String code) { return jdbc.query("SELECT * FROM service_catalog_form_configuration_history WHERE code=? ORDER BY form_version DESC", (rs,n)->map(rs), code); }
    @Override public void savePublishedSnapshot(ManagedFormConfiguration c) {
        jdbc.update("""
            INSERT INTO service_catalog_form_configuration_history (id, code, name, summary, ticket_type, category_code, applicable_organization_ids, fields_json, tag_policy_json, lifecycle_status, version, form_version, schema_hash, change_reason, created_by_iam_user_id, last_modified_by_iam_user_id, created_at, updated_at, published_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, c.id(), c.code(), c.name(), c.summary(), c.ticketType().name(), c.categoryCode(), write(c.applicableOrganizationIds()), write(c.fields()), write(c.tagPolicy()), c.status().name(), c.version(), c.formVersion(), c.schemaHash(), c.changeReason(), c.createdByIamUserId(), c.lastModifiedByIamUserId(), Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()), timestamp(c.publishedAt()));
    }
    @Override public ManagedFormConfiguration save(ManagedFormConfiguration c, long expectedVersion) {
        int changed = jdbc.update("""
            UPDATE service_catalog_form_configuration SET name=?, summary=?, ticket_type=?, category_code=?, applicable_organization_ids=?, fields_json=?, tag_policy_json=?, lifecycle_status=?, version=?, form_version=?, schema_hash=?, change_reason=?, last_modified_by_iam_user_id=?, updated_at=?, published_at=? WHERE id=? AND version=?
            """, c.name(), c.summary(), c.ticketType().name(), c.categoryCode(), write(c.applicableOrganizationIds()), write(c.fields()), write(c.tagPolicy()), c.status().name(), c.version(), c.formVersion(), c.schemaHash(), c.changeReason(), c.lastModifiedByIamUserId(), Timestamp.from(c.updatedAt()), timestamp(c.publishedAt()), c.id(), expectedVersion);
        if (changed == 1) return c;
        if (expectedVersion != 0 || findById(c.id()).isPresent()) throw new FormConfigurationConflictException();
        try {
            jdbc.update("""
                INSERT INTO service_catalog_form_configuration (id, code, name, summary, ticket_type, category_code, applicable_organization_ids, fields_json, tag_policy_json, lifecycle_status, version, form_version, schema_hash, change_reason, created_by_iam_user_id, last_modified_by_iam_user_id, created_at, updated_at, published_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                c.id(), c.code(), c.name(), c.summary(), c.ticketType().name(), c.categoryCode(), write(c.applicableOrganizationIds()), write(c.fields()), write(c.tagPolicy()), c.status().name(), c.version(), c.formVersion(), c.schemaHash(), c.changeReason(), c.createdByIamUserId(), c.lastModifiedByIamUserId(), Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()), timestamp(c.publishedAt()));
        } catch (org.springframework.dao.DuplicateKeyException e) { throw new FormConfigurationConflictException(); }
        return c;
    }
    @Override public FormPublicationRequest savePublicationRequest(FormPublicationRequest r) { jdbc.update("INSERT INTO service_catalog_form_publication_request (id,catalog_item_id,requested_version,reason,applicant_iam_user_id,lifecycle_status,requested_at,decided_by_iam_user_id,decided_at) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE lifecycle_status=VALUES(lifecycle_status), decided_by_iam_user_id=VALUES(decided_by_iam_user_id), decided_at=VALUES(decided_at)", r.id(),r.catalogItemId(),r.requestedVersion(),r.reason(),r.applicantIamUserId(),r.status().name(),Timestamp.from(r.requestedAt()),r.decidedByIamUserId(),timestamp(r.decidedAt())); return r; }
    @Override public Optional<FormPublicationRequest> findPublicationRequest(String id) { return jdbc.query("SELECT * FROM service_catalog_form_publication_request WHERE id=?", (rs,n)->new FormPublicationRequest(rs.getString("id"),rs.getString("catalog_item_id"),rs.getLong("requested_version"),rs.getString("reason"),rs.getString("applicant_iam_user_id"),FormConfigurationStatus.valueOf(rs.getString("lifecycle_status")),rs.getTimestamp("requested_at").toInstant(),rs.getString("decided_by_iam_user_id"),rs.getTimestamp("decided_at")==null?null:rs.getTimestamp("decided_at").toInstant()),id).stream().findFirst(); }
    private ManagedFormConfiguration map(java.sql.ResultSet rs) throws java.sql.SQLException { return new ManagedFormConfiguration(rs.getString("id"),rs.getString("code"),rs.getString("name"),rs.getString("summary"),TicketType.valueOf(rs.getString("ticket_type")),rs.getString("category_code"),read(rs.getString("applicable_organization_ids"),new TypeReference<List<String>>(){}),read(rs.getString("fields_json"),new TypeReference<List<ConfiguredFormField>>(){}),read(rs.getString("tag_policy_json"),TagPolicy.class),FormConfigurationStatus.valueOf(rs.getString("lifecycle_status")),rs.getLong("version"),rs.getInt("form_version"),rs.getString("schema_hash"),rs.getString("change_reason"),rs.getString("created_by_iam_user_id"),rs.getString("last_modified_by_iam_user_id"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getTimestamp("published_at")==null?null:rs.getTimestamp("published_at").toInstant()); }
    private Timestamp timestamp(java.time.Instant v) { return v == null ? null : Timestamp.from(v); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException("Unable to serialize configuration",e); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("Invalid configuration JSON",e); } }
    private <T> T read(String value, TypeReference<T> type) { try { return json.readValue(value,type); } catch(Exception e) { throw new IllegalStateException("Invalid configuration JSON",e); } }
}
