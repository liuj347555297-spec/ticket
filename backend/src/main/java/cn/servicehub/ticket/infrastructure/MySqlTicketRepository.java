package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketQuery;
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
    public List<Ticket> findAll(TicketQuery query) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ticket WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();
        if (query.status() != null) {
            sql.append(" AND status = ?");
            parameters.add(query.status().name());
        }
        if (query.type() != null) {
            sql.append(" AND type = ?");
            parameters.add(query.type().name());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            sql.append(" AND (LOWER(title) LIKE ? OR LOWER(description) LIKE ? OR LOWER(CAST(tags AS CHAR)) LIKE ?)");
            String keyword = "%" + query.keyword().toLowerCase(java.util.Locale.ROOT) + "%";
            parameters.add(keyword);
            parameters.add(keyword);
            parameters.add(keyword);
        }
        sql.append(" ORDER BY created_at DESC, id ASC");
        return jdbcTemplate.query(sql.toString(), ticketRowMapper, parameters.toArray());
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
              id, type, status, priority, title, description, structured_fields, tags, related_configuration_item_ids,
              requester_iam_user_id, requester_display_name, requester_organization_name, requester_position_name,
              requester_captured_at, service_catalog_item_id, service_catalog_item_name, created_at, updated_at, version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, ticket.id(), ticket.type().name(), ticket.status().name(), ticket.priority().name(), ticket.title(), ticket.description(),
            asJson(ticket.structuredFields()), asJson(ticket.tags()), asJson(ticket.relatedConfigurationItemIds()),
            ticket.requester().iamUserId(), ticket.requester().displayName(), ticket.requester().organizationName(),
            ticket.requester().positionName(), Timestamp.from(ticket.requester().capturedAt()), ticket.serviceCatalogItem().id(),
            ticket.serviceCatalogItem().name(), Timestamp.from(ticket.createdAt()), Timestamp.from(ticket.updatedAt()), ticket.version());
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
                resultSet.getString("title"), resultSet.getString("description"),
                objectMapper.readValue(resultSet.getString("structured_fields"), new TypeReference<Map<String, Object>>() { }),
                objectMapper.readValue(resultSet.getString("tags"), new TypeReference<List<TicketTag>>() { }),
                objectMapper.readValue(resultSet.getString("related_configuration_item_ids"), new TypeReference<List<String>>() { }),
                new IdentitySnapshot(resultSet.getString("requester_iam_user_id"), resultSet.getString("requester_display_name"),
                    resultSet.getString("requester_organization_name"), resultSet.getString("requester_position_name"),
                    resultSet.getTimestamp("requester_captured_at").toInstant()),
                new ServiceCatalogSummary(resultSet.getString("service_catalog_item_id"), resultSet.getString("service_catalog_item_name")),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant(), resultSet.getLong("version"));
        } catch (Exception exception) {
            throw new SQLException("Unable to map persisted ticket", exception);
        }
    }

    private record IdempotencyRecord(String requestFingerprint, String ticketId) {
    }
}
