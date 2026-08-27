package cn.servicehub.integration.infrastructure;

import cn.servicehub.integration.domain.NormalizedAlert;
import cn.servicehub.integration.domain.NormalizedAlertRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlNormalizedAlertRepository implements NormalizedAlertRepository {
    private final JdbcTemplate jdbc; public MySqlNormalizedAlertRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Optional<NormalizedAlert> findBySourceAndEventId(String source,String eventId) { return jdbc.query("SELECT * FROM external_normalized_alert WHERE source_code=? AND source_event_id=?",(rs,n)->map(rs),source,eventId).stream().findFirst(); }
    @Override public NormalizedAlert save(NormalizedAlert alert) { try { jdbc.update("INSERT INTO external_normalized_alert(id,source_code,source_event_id,fingerprint,severity,title,ci_id,alert_status,idempotency_status,ticket_id,occurred_at,received_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",alert.id(),alert.sourceCode(),alert.sourceEventId(),alert.fingerprint(),alert.severity(),alert.title(),alert.configurationItemId(),alert.status(),alert.idempotencyStatus(),alert.ticketId(),Timestamp.from(alert.occurredAt()),Timestamp.from(alert.receivedAt())); return alert; } catch(DuplicateKeyException ignored) { return findBySourceAndEventId(alert.sourceCode(),alert.sourceEventId()).orElseThrow(); } }
    @Override public List<NormalizedAlert> findRecent(int limit) { return jdbc.query("SELECT * FROM external_normalized_alert ORDER BY received_at DESC LIMIT ?",(rs,n)->map(rs),limit); }
    private static NormalizedAlert map(java.sql.ResultSet rs) throws java.sql.SQLException { return new NormalizedAlert(rs.getString("id"),rs.getString("source_code"),rs.getString("source_event_id"),rs.getString("fingerprint"),rs.getString("severity"),rs.getString("title"),rs.getString("ci_id"),rs.getString("alert_status"),rs.getString("idempotency_status"),rs.getString("ticket_id"),rs.getTimestamp("occurred_at").toInstant(),rs.getTimestamp("received_at").toInstant()); }
}
