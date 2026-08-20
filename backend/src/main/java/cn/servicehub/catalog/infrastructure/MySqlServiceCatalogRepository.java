package cn.servicehub.catalog.infrastructure;

import cn.servicehub.catalog.domain.CaseMatchRecord;
import cn.servicehub.catalog.domain.CaseMatchRule;
import cn.servicehub.catalog.domain.CatalogPublicationStatus;
import cn.servicehub.catalog.domain.DictionaryDefinition;
import cn.servicehub.catalog.domain.DictionaryOption;
import cn.servicehub.catalog.domain.FormFieldDefinition;
import cn.servicehub.catalog.domain.FormFieldType;
import cn.servicehub.catalog.domain.KnowledgeCase;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.catalog.domain.StandardTag;
import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL read model. Catalog administration is deliberately outside this requester-facing adapter. */
@Repository
@Profile("mysql")
public class MySqlServiceCatalogRepository implements ServiceCatalogRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MySqlServiceCatalogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ServiceCatalogItem> findPublishedItems() {
        return jdbcTemplate.query("""
            SELECT id, name, description, publication_status, supported_ticket_types
            FROM service_catalog_item WHERE publication_status = 'PUBLISHED' ORDER BY name, id
            """, (rs, row) -> item(rs.getString("id"), rs.getString("name"), rs.getString("description"),
            rs.getString("publication_status"), rs.getString("supported_ticket_types")));
    }

    @Override
    public Optional<ServiceCatalogItem> findById(String id) {
        return jdbcTemplate.query("""
            SELECT id, name, description, publication_status, supported_ticket_types
            FROM service_catalog_item WHERE id = ?
            """, (rs, row) -> item(rs.getString("id"), rs.getString("name"), rs.getString("description"),
            rs.getString("publication_status"), rs.getString("supported_ticket_types")), id).stream().findFirst();
    }

    @Override
    public Optional<DictionaryDefinition> findDictionary(String code) {
        return jdbcTemplate.query("""
            SELECT code, name, publication_status FROM service_catalog_dictionary WHERE code = ?
            """, (rs, row) -> new DictionaryDefinition(rs.getString("code"), rs.getString("name"),
            CatalogPublicationStatus.valueOf(rs.getString("publication_status")), options(rs.getString("code"))), code).stream().findFirst();
    }

    @Override
    public List<StandardTag> findEnabledStandardTags() {
        return jdbcTemplate.query("SELECT tag_name, tag_label FROM service_catalog_tag WHERE enabled = TRUE ORDER BY tag_name",
            (rs, row) -> new StandardTag(rs.getString("tag_name"), rs.getString("tag_label")));
    }

    @Override
    public List<KnowledgeCase> findPublishedCases() {
        return jdbcTemplate.query("""
            SELECT id, title, resolution_summary, publication_status FROM knowledge_case
            WHERE publication_status = 'PUBLISHED'
            """, (rs, row) -> new KnowledgeCase(rs.getString("id"), rs.getString("title"),
            rs.getString("resolution_summary"), CatalogPublicationStatus.valueOf(rs.getString("publication_status"))));
    }

    @Override
    public List<CaseMatchRule> findEnabledRules() {
        return jdbcTemplate.query("""
            SELECT id, knowledge_case_id, enabled, catalog_item_id, configuration_item_id, field_code, field_value,
                   tag_name, tag_kind, error_code, keyword, score
            FROM knowledge_case_match_rule WHERE enabled = TRUE
            """, (rs, row) -> new CaseMatchRule(rs.getLong("id"), rs.getString("knowledge_case_id"), rs.getBoolean("enabled"),
            rs.getString("catalog_item_id"), rs.getString("configuration_item_id"), rs.getString("field_code"),
            rs.getString("field_value"), rs.getString("tag_name"), nullableTagKind(rs.getString("tag_kind")),
            rs.getString("error_code"), rs.getString("keyword"), rs.getInt("score")));
    }

    @Override
    public void saveMatchRecord(CaseMatchRecord record) {
        jdbcTemplate.update("""
            INSERT INTO knowledge_case_match_record
              (id, actor_iam_user_id, catalog_item_id, criteria_hash, matched_case_ids, matched_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, record.id(), record.actorIamUserId(), record.catalogItemId(), record.criteriaHash(), asJson(record.matchedCaseIds()),
            Timestamp.from(record.matchedAt()));
    }

    private ServiceCatalogItem item(String id, String name, String description, String publicationStatus, String ticketTypesJson) {
        return new ServiceCatalogItem(id, name, description, CatalogPublicationStatus.valueOf(publicationStatus),
            ticketTypes(ticketTypesJson), fields(id));
    }

    private List<FormFieldDefinition> fields(String catalogItemId) {
        return jdbcTemplate.query("""
            SELECT field_code, field_label, field_type, required, max_length, dictionary_code, sort_order
            FROM service_catalog_form_field WHERE catalog_item_id = ? ORDER BY sort_order, field_code
            """, (rs, row) -> new FormFieldDefinition(rs.getString("field_code"), rs.getString("field_label"),
            FormFieldType.valueOf(rs.getString("field_type")), rs.getBoolean("required"), (Integer) rs.getObject("max_length"),
            rs.getString("dictionary_code"), rs.getInt("sort_order")), catalogItemId);
    }

    private List<DictionaryOption> options(String code) {
        return jdbcTemplate.query("""
            SELECT option_code, option_label, enabled, sort_order FROM service_catalog_dictionary_option
            WHERE dictionary_code = ? ORDER BY sort_order, option_code
            """, (rs, row) -> new DictionaryOption(rs.getString("option_code"), rs.getString("option_label"),
            rs.getBoolean("enabled"), rs.getInt("sort_order")), code);
    }

    private Set<TicketType> ticketTypes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Set<TicketType>>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid persisted supported ticket type configuration", exception);
        }
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize case-match record", exception);
        }
    }

    private TicketTag.Kind nullableTagKind(String value) {
        return value == null ? null : TicketTag.Kind.valueOf(value);
    }
}
