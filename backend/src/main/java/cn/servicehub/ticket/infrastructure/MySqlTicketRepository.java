package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketQuery;
import cn.servicehub.ticket.domain.TicketPageSlice;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketTag;
import cn.servicehub.ticket.domain.TicketType;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketPriority;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 8 persistence adapter. It is activated only by the {@code mysql} Spring profile, so an
 * absent local database never changes the secure in-memory development and test behaviour.
 */
@Repository
@Profile("mysql")
public class MySqlTicketRepository implements TicketRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Ticket> ticketRowMapper;

    public MySqlTicketRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.ticketRowMapper = (resultSet, rowNum) -> mapTicket(resultSet);
    }

    @Override
    @Transactional
    public CreateTicketResult createIdempotently(String actorIamUserId, String idempotencyKey, String requestFingerprint,
                                                  Supplier<Ticket> ticketSupplier) {
        Optional<IdempotencyRecord> existing = findIdempotencyRecord(actorIamUserId, idempotencyKey);
        if (existing.isPresent()) {
            return replayOrReject(existing.get(), requestFingerprint);
        }

        Ticket ticket = ticketSupplier.get();
        boolean ticketInserted = false;
        try {
            insertTicket(ticket);
            ticketInserted = true;
            jdbcTemplate.update("""
                INSERT INTO ticket_idempotency (actor_iam_user_id, idempotency_key, request_fingerprint, ticket_id, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, actorIamUserId, idempotencyKey, requestFingerprint, ticket.id(), Timestamp.from(ticket.createdAt()));
            return new CreateTicketResult(ticket, false);
        } catch (DuplicateKeyException duplicateKeyException) {
            // A concurrent request may win the unique (actor, key) constraint. Remove the
            // unreferenced ticket created by this transaction, then return only the original result.
            IdempotencyRecord record = findIdempotencyRecord(actorIamUserId, idempotencyKey).orElse(null);
            if (record == null) {
                // It was not an idempotency-key collision (for example, an unexpected ticket ID
                // collision). Never delete a row that this call did not successfully insert.
                throw duplicateKeyException;
            }
            if (ticketInserted) {
                jdbcTemplate.update("DELETE FROM ticket WHERE id = ?", ticket.id());
            }
            return replayOrReject(record, requestFingerprint);
        }
    }

    @Override
    public Optional<Ticket> findById(String ticketId) {
        List<Ticket> tickets = jdbcTemplate.query("SELECT * FROM ticket WHERE id = ?", ticketRowMapper, ticketId);
        return tickets.stream().findFirst();
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<Ticket> findAll(TicketQuery query) {
        throw new UnsupportedOperationException("Unbounded MySQL ticket queries are forbidden; use findPage with an authorization scope");
    }

    @Override
    public TicketPageSlice findPage(TicketQuery query) {
        if (query.accessScope() == null || query.snapshotAt() == null || query.pageSize() < 1 || query.pageSize() > 100) {
            throw new IllegalArgumentException("Bounded ticket query is required");
        }
        SqlParts pageWhere = where(query, true);
        List<Object> pageArguments = new ArrayList<>(pageWhere.arguments());
        pageArguments.add(query.pageSize() + 1);
        List<Ticket> rows = jdbcTemplate.query("SELECT t.* FROM ticket t" + pageWhere.sql()
            + " ORDER BY t.created_at DESC, t.id DESC LIMIT ?", ticketRowMapper, pageArguments.toArray());
        boolean hasMore = rows.size() > query.pageSize();
        List<Ticket> items = hasMore ? List.copyOf(rows.subList(0, query.pageSize())) : List.copyOf(rows);
        SqlParts countWhere = where(query, false);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ticket t" + countWhere.sql(), Long.class,
            countWhere.arguments().toArray());
        return new TicketPageSlice(items, hasMore, count == null ? 0 : count);
    }

    private SqlParts where(TicketQuery query, boolean includeCursor) {
        StringBuilder sql = new StringBuilder(" WHERE t.created_at <= ?");
        List<Object> args = new ArrayList<>(); args.add(Timestamp.from(query.snapshotAt()));
        if (query.status() != null) { sql.append(" AND t.status=?"); args.add(query.status().name()); }
        if (query.type() != null) { sql.append(" AND t.type=?"); args.add(query.type().name()); }
        if (query.priority() != null) { sql.append(" AND t.priority=?"); args.add(query.priority().name()); }
        if (notBlank(query.serviceCatalogItemId())) {
            sql.append(" AND (LOWER(t.service_catalog_item_id) LIKE ? ESCAPE '!' OR LOWER(t.service_catalog_item_name) LIKE ? ESCAPE '!')");
            String value = "%" + escapeLikeLiteral(query.serviceCatalogItemId().trim().toLowerCase(java.util.Locale.ROOT)) + "%";
            args.add(value); args.add(value);
        }
        if (notBlank(query.requesterOrganizationId())) {
            sql.append(" AND (LOWER(t.requester_organization_id) LIKE ? ESCAPE '!' OR LOWER(t.requester_organization_name) LIKE ? ESCAPE '!')");
            String value = "%" + escapeLikeLiteral(query.requesterOrganizationId().trim().toLowerCase(java.util.Locale.ROOT)) + "%";
            args.add(value); args.add(value);
        }
        if (query.createdFrom() != null) { sql.append(" AND t.created_at>=?"); args.add(Timestamp.from(query.createdFrom())); }
        if (query.createdTo() != null) { sql.append(" AND t.created_at<?"); args.add(Timestamp.from(query.createdTo())); }
        if (notBlank(query.keyword())) {
            sql.append(" AND (LOWER(t.id) LIKE ? ESCAPE '!' OR LOWER(t.title) LIKE ? ESCAPE '!' OR LOWER(t.description) LIKE ? ESCAPE '!' OR LOWER(t.service_catalog_item_name) LIKE ? ESCAPE '!' OR LOWER(CAST(t.tags AS CHAR)) LIKE ? ESCAPE '!')");
            String value = "%" + escapeLikeLiteral(query.keyword().trim().toLowerCase(java.util.Locale.ROOT)) + "%";
            args.add(value); args.add(value); args.add(value); args.add(value); args.add(value);
        }
        appendAuthorization(sql, args, query);
        appendQueue(sql, args, query);
        if (includeCursor && query.afterCreatedAt() != null) {
            if (!notBlank(query.afterId())) throw new IllegalArgumentException("Ticket cursor key is incomplete");
            sql.append(" AND (t.created_at<? OR (t.created_at=? AND t.id<?))");
            Timestamp after = Timestamp.from(query.afterCreatedAt()); args.add(after); args.add(after); args.add(query.afterId());
        }
        return new SqlParts(sql.toString(), args);
    }

    private void appendAuthorization(StringBuilder sql, List<Object> args, TicketQuery query) {
        var scope = query.accessScope();
        sql.append(" AND (t.requester_iam_user_id=?"); args.add(scope.actorIamUserId());
        if (scope.scopedTicketRole() && !scope.failClosed()) {
            if (scope.legacyDirectBypass()) {
                sql.append(" OR 1=1");
            } else if (scope.hasAnyScope()) {
                sql.append(" OR ("); boolean needsAnd = false;
                needsAnd = appendIn(sql, args, "t.requester_organization_id", scope.organizationIds(), needsAnd);
                needsAnd = appendIn(sql, args, "t.service_catalog_item_id", scope.serviceCatalogItemIds(), needsAnd);
                if (!scope.serviceSystemCodes().isEmpty()) {
                    if (needsAnd) sql.append(" AND ");
                    sql.append("EXISTS (SELECT 1 FROM ticket_service_system_snapshot ss WHERE ss.ticket_id=t.id AND ss.system_code IN (");
                    placeholders(sql, scope.serviceSystemCodes().size()); sql.append("))"); args.addAll(scope.serviceSystemCodes()); needsAnd = true;
                }
                if (!scope.configurationItemIds().isEmpty()) {
                    if (needsAnd) sql.append(" AND ");
                    sql.append("EXISTS (SELECT 1 FROM ticket_configuration_item tc WHERE tc.ticket_id=t.id AND tc.ci_id IN (");
                    placeholders(sql, scope.configurationItemIds().size()); sql.append("))"); args.addAll(scope.configurationItemIds());
                }
                sql.append(")");
            }
        }
        sql.append(")");
    }

    private void appendQueue(StringBuilder sql, List<Object> args, TicketQuery query) {
        var scope = query.accessScope();
        switch (query.queue()) {
            case ALL -> { }
            case MY_REQUESTED -> { sql.append(" AND t.requester_iam_user_id=?"); args.add(scope.actorIamUserId()); }
            case DRAFTS -> { sql.append(" AND t.requester_iam_user_id=? AND t.status='DRAFT'"); args.add(scope.actorIamUserId()); }
            case MY_TODO -> {
                sql.append(" AND EXISTS (SELECT 1 FROM ticket_workflow_task wt WHERE wt.ticket_id=t.id AND wt.status IN ('OPEN','CLAIMED') AND (wt.assignee_iam_user_id=? OR (wt.assignee_iam_user_id IS NULL AND (wt.candidate_iam_user_id=?");
                args.add(scope.actorIamUserId()); args.add(scope.actorIamUserId());
                if (!scope.roleCodes().isEmpty()) { sql.append(" OR wt.candidate_role IN ("); placeholders(sql, scope.roleCodes().size()); sql.append(")"); args.addAll(scope.roleCodes()); }
                sql.append("))))");
            }
            case TODAY_DUE -> {
                sql.append(" AND t.status NOT IN ('RESOLVED','CLOSED','CANCELLED') AND EXISTS (SELECT 1 FROM ticket_workflow_task wt WHERE wt.ticket_id=t.id AND wt.status IN ('OPEN','CLAIMED') AND (wt.assignee_iam_user_id=? OR (wt.assignee_iam_user_id IS NULL AND (wt.candidate_iam_user_id=?");
                args.add(scope.actorIamUserId()); args.add(scope.actorIamUserId());
                if (!scope.roleCodes().isEmpty()) { sql.append(" OR wt.candidate_role IN ("); placeholders(sql, scope.roleCodes().size()); sql.append(")"); args.addAll(scope.roleCodes()); }
                if (query.todayDueFrom() == null || query.todayDueTo() == null) throw new IllegalArgumentException("Today-due boundaries are required");
                sql.append(")))) AND EXISTS (SELECT 1 FROM ticket_sla_target st WHERE st.ticket_id=t.id AND st.resolved_at IS NULL AND ((st.first_responded_at IS NULL AND st.response_due_at>=? AND st.response_due_at<?) OR (st.resolution_due_at>=? AND st.resolution_due_at<?)))");
                args.add(Timestamp.from(query.todayDueFrom())); args.add(Timestamp.from(query.todayDueTo())); args.add(Timestamp.from(query.todayDueFrom())); args.add(Timestamp.from(query.todayDueTo()));
            }
            case MY_DONE -> { sql.append(" AND EXISTS (SELECT 1 FROM ticket_workflow_task wt WHERE wt.ticket_id=t.id AND wt.status='COMPLETED' AND wt.assignee_iam_user_id=?)"); args.add(scope.actorIamUserId()); }
            case TODAY_COMPLETED -> { sql.append(" AND t.status IN ('RESOLVED','CLOSED') AND DATE(t.updated_at)=UTC_DATE() AND EXISTS (SELECT 1 FROM ticket_workflow_task wt WHERE wt.ticket_id=t.id AND wt.status='COMPLETED' AND wt.assignee_iam_user_id=?)"); args.add(scope.actorIamUserId()); }
            case TO_READ -> { sql.append(" AND EXISTS (SELECT 1 FROM notification n WHERE n.ticket_id=t.id AND n.recipient_iam_user_id=? AND n.read_at IS NULL)"); args.add(scope.actorIamUserId()); }
            case OVERDUE -> {
                sql.append(" AND EXISTS (SELECT 1 FROM ticket_workflow_task wt WHERE wt.ticket_id=t.id AND wt.status IN ('OPEN','CLAIMED') AND (wt.assignee_iam_user_id=? OR (wt.assignee_iam_user_id IS NULL AND (wt.candidate_iam_user_id=?");
                args.add(scope.actorIamUserId()); args.add(scope.actorIamUserId());
                if (!scope.roleCodes().isEmpty()) { sql.append(" OR wt.candidate_role IN ("); placeholders(sql, scope.roleCodes().size()); sql.append(")"); args.addAll(scope.roleCodes()); }
                sql.append(")))) AND EXISTS (SELECT 1 FROM ticket_sla_target st WHERE st.ticket_id=t.id AND st.risk_level='BREACHED')");
            }
        }
        if(notBlank(query.teamQueueCode())){
            if(query.teamQueueScope()==null)throw new IllegalArgumentException("Authorized team queue scope is required");
            sql.append(" AND EXISTS (SELECT 1 FROM ticket_workflow_task tq WHERE tq.ticket_id=t.id AND tq.queue_code=? AND tq.status IN ('OPEN','CLAIMED'))");args.add(query.teamQueueCode());
            appendTeamScope(sql,args,query.teamQueueScope());
        }
    }

    private void appendTeamScope(StringBuilder sql,List<Object>args,cn.servicehub.ticket.domain.TicketAccessScope scope){sql.append(" AND (");boolean and=false;and=appendIn(sql,args,"t.requester_organization_id",scope.organizationIds(),and);and=appendIn(sql,args,"t.service_catalog_item_id",scope.serviceCatalogItemIds(),and);if(!scope.serviceSystemCodes().isEmpty()){if(and)sql.append(" AND ");sql.append("EXISTS (SELECT 1 FROM ticket_service_system_snapshot qss WHERE qss.ticket_id=t.id AND qss.system_code IN (");placeholders(sql,scope.serviceSystemCodes().size());sql.append("))");args.addAll(scope.serviceSystemCodes());and=true;}if(!scope.configurationItemIds().isEmpty()){if(and)sql.append(" AND ");sql.append("EXISTS (SELECT 1 FROM ticket_configuration_item qci WHERE qci.ticket_id=t.id AND qci.ci_id IN (");placeholders(sql,scope.configurationItemIds().size());sql.append("))");args.addAll(scope.configurationItemIds());and=true;}if(!and)sql.append("1=0");sql.append(")");}

    private boolean appendIn(StringBuilder sql, List<Object> args, String column, java.util.Set<String> values, boolean needsAnd) {
        if (values.isEmpty()) return needsAnd;
        if (needsAnd) sql.append(" AND "); sql.append(column).append(" IN ("); placeholders(sql, values.size()); sql.append(")"); args.addAll(values); return true;
    }
    private static void placeholders(StringBuilder sql, int count) { sql.append(String.join(",", java.util.Collections.nCopies(count, "?"))); }
    private static boolean notBlank(String value) { return value != null && !value.isBlank(); }
    static String escapeLikeLiteral(String value) { return value.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
    private record SqlParts(String sql, List<Object> arguments) { }

    @Override
    public boolean updateStatus(String ticketId, long expectedVersion, TicketStatus status, Instant updatedAt) {
        return jdbcTemplate.update("""
            UPDATE ticket SET status = ?, updated_at = ?, version = version + 1
            WHERE id = ? AND version = ?
            """, status.name(), Timestamp.from(updatedAt), ticketId, expectedVersion) == 1;
    }

    @Override
    public boolean updateDescription(String ticketId, long expectedVersion, String description, cn.servicehub.ticket.domain.TicketDescriptionFormat format, String descriptionHtml, Instant updatedAt) {
        return jdbcTemplate.update("""
            UPDATE ticket SET description = ?, description_format = ?, description_html = ?, updated_at = ?, version = version + 1
            WHERE id = ? AND version = ?
            """, description, format.name(), descriptionHtml, Timestamp.from(updatedAt), ticketId, expectedVersion) == 1;
    }

    @Override
    @Transactional
    public long nextTicketSequence(LocalDate businessDate) {
        jdbcTemplate.update("""
            INSERT INTO ticket_number_sequence (business_date, sequence_value)
            VALUES (?, 1)
            ON DUPLICATE KEY UPDATE sequence_value = sequence_value + 1
            """, businessDate);
        Long sequence = jdbcTemplate.queryForObject(
            "SELECT sequence_value FROM ticket_number_sequence WHERE business_date = ? FOR UPDATE", Long.class, businessDate);
        if (sequence == null) {
            throw new IllegalStateException("Ticket number sequence was not allocated");
        }
        return sequence;
    }

    private CreateTicketResult replayOrReject(IdempotencyRecord record, String requestFingerprint) {
        if (!record.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflictException();
        }
        return findById(record.ticketId()).map(ticket -> new CreateTicketResult(ticket, true))
            .orElseThrow(() -> new IllegalStateException("Idempotency record refers to a missing ticket"));
    }

    private Optional<IdempotencyRecord> findIdempotencyRecord(String actorIamUserId, String idempotencyKey) {
        List<IdempotencyRecord> records = jdbcTemplate.query("""
            SELECT request_fingerprint, ticket_id FROM ticket_idempotency
            WHERE actor_iam_user_id = ? AND idempotency_key = ?
            """, (resultSet, rowNum) -> new IdempotencyRecord(resultSet.getString(1), resultSet.getString(2)),
            actorIamUserId, idempotencyKey);
        return records.stream().findFirst();
    }

    private void insertTicket(Ticket ticket) {
        jdbcTemplate.update("""
            INSERT INTO ticket (
              id, type, status, priority, title, description, description_format, description_html, structured_fields, tags, related_configuration_item_ids,
              requester_iam_user_id, requester_display_name, requester_organization_id, requester_organization_name, requester_position_name,
              requester_captured_at, service_catalog_item_id, service_catalog_item_name, service_catalog_form_version, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, ticket.id(), ticket.type().name(), ticket.status().name(), ticket.priority().name(), ticket.title(), ticket.description(), ticket.descriptionFormat().name(), ticket.descriptionHtml(),
            asJson(ticket.structuredFields()), asJson(ticket.tags()), asJson(ticket.relatedConfigurationItemIds()),
            ticket.requester().iamUserId(), ticket.requester().displayName(), ticket.requester().organizationId(), ticket.requester().organizationName(),
            ticket.requester().positionName(), Timestamp.from(ticket.requester().capturedAt()), ticket.serviceCatalogItem().id(),
            ticket.serviceCatalogItem().name(), ticket.serviceCatalogFormVersion(), Timestamp.from(ticket.createdAt()), Timestamp.from(ticket.updatedAt()), ticket.version());
    }

    private String asJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Ticket JSON payload cannot be persisted", exception);
        }
    }

    private Ticket mapTicket(ResultSet resultSet) throws SQLException {
        try {
            return new Ticket(resultSet.getString("id"), TicketType.valueOf(resultSet.getString("type")),
                TicketStatus.valueOf(resultSet.getString("status")), TicketPriority.valueOf(resultSet.getString("priority")),
                resultSet.getString("title"), resultSet.getString("description"), cn.servicehub.ticket.domain.TicketDescriptionFormat.valueOf(resultSet.getString("description_format")), resultSet.getString("description_html"),
                objectMapper.readValue(resultSet.getString("structured_fields"), new TypeReference<Map<String, Object>>() { }),
                objectMapper.readValue(resultSet.getString("tags"), new TypeReference<List<TicketTag>>() { }),
                objectMapper.readValue(resultSet.getString("related_configuration_item_ids"), new TypeReference<List<String>>() { }),
                new IdentitySnapshot(resultSet.getString("requester_iam_user_id"), resultSet.getString("requester_display_name"),
                    resultSet.getString("requester_organization_id"), resultSet.getString("requester_organization_name"), resultSet.getString("requester_position_name"),
                    resultSet.getTimestamp("requester_captured_at").toInstant()),
                new ServiceCatalogSummary(resultSet.getString("service_catalog_item_id"), resultSet.getString("service_catalog_item_name")),
                resultSet.getInt("service_catalog_form_version"), resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("version"));
        } catch (Exception exception) {
            throw new SQLException("Unable to map persisted ticket", exception);
        }
    }

    private record IdempotencyRecord(String requestFingerprint, String ticketId) {
    }
}
