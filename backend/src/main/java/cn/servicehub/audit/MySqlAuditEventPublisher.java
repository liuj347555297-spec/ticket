package cn.servicehub.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** MySQL production sink. Append failure is propagated, so callers cannot silently treat a change as audited. */
@Component
@Profile("mysql")
public class MySqlAuditEventPublisher implements AuditEventPublisher {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public MySqlAuditEventPublisher(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @Override public void publish(AuditEvent event) {
        try {
            jdbc.update("INSERT INTO audit_event (id, occurred_at, request_id, actor_iam_user_id, action_code, resource_type, resource_id, attributes_json) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))",
                UUID.randomUUID().toString(), Timestamp.from(event.occurredAt()), event.requestId(), event.actorIamUserId(), event.action(),
                event.resourceType(), event.resourceId(), json.writeValueAsString(event.attributes()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Audit evidence cannot be serialized", exception);
        }
    }
}
