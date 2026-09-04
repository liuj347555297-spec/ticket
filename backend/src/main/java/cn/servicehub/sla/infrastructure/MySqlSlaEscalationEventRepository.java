package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.SlaEscalationEventRepository;
import cn.servicehub.sla.domain.SlaRiskLevel;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository @Profile("mysql")
public class MySqlSlaEscalationEventRepository implements SlaEscalationEventRepository {
    private final JdbcTemplate jdbc; public MySqlSlaEscalationEventRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean appendIfAbsent(String ticketId,long targetVersion,String eventCode,SlaRiskLevel riskLevel,Instant occurredAt) { try { return jdbc.update("INSERT INTO sla_escalation_event (id,ticket_id,target_version,event_code,risk_level,occurred_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),ticketId,targetVersion,eventCode,riskLevel.name(),Timestamp.from(occurredAt))==1; } catch(DuplicateKeyException ignored) { return false; } }
}
