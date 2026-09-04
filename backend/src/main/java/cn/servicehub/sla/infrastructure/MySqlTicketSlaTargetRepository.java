package cn.servicehub.sla.infrastructure;

import cn.servicehub.sla.domain.SlaRiskLevel;
import cn.servicehub.sla.domain.TicketSlaTarget;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import java.sql.Timestamp;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlTicketSlaTargetRepository implements TicketSlaTargetRepository {
    private final JdbcTemplate jdbc;
    public MySqlTicketSlaTargetRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<TicketSlaTarget> findByTicketId(String ticketId) { return jdbc.query("SELECT * FROM ticket_sla_target WHERE ticket_id = ?", (rs, n) -> new TicketSlaTarget(rs.getString("ticket_id"), rs.getString("policy_id"), rs.getString("policy_name_snapshot"), rs.getString("calendar_key_snapshot"), rs.getTimestamp("response_due_at").toInstant(), rs.getTimestamp("resolution_due_at").toInstant(), instant(rs, "first_responded_at"), instant(rs, "resolved_at"), rs.getLong("paused_seconds"), instant(rs, "pause_started_at"), SlaRiskLevel.valueOf(rs.getString("risk_level")), rs.getBoolean("response_breached"), rs.getBoolean("resolution_breached"), rs.getTimestamp("calculated_at").toInstant(), rs.getLong("version")), ticketId).stream().findFirst(); }
    public java.util.List<String> findBreachedTicketIds() { return jdbc.queryForList("SELECT ticket_id FROM ticket_sla_target WHERE risk_level='BREACHED'", String.class); }
    public java.util.List<TicketSlaTarget> findOpenTargets(int limit) { return jdbc.query("SELECT * FROM ticket_sla_target WHERE resolved_at IS NULL ORDER BY resolution_due_at ASC LIMIT ?", (rs,n) -> new TicketSlaTarget(rs.getString("ticket_id"), rs.getString("policy_id"), rs.getString("policy_name_snapshot"), rs.getString("calendar_key_snapshot"), rs.getTimestamp("response_due_at").toInstant(), rs.getTimestamp("resolution_due_at").toInstant(), instant(rs,"first_responded_at"), instant(rs,"resolved_at"), rs.getLong("paused_seconds"), instant(rs,"pause_started_at"), SlaRiskLevel.valueOf(rs.getString("risk_level")), rs.getBoolean("response_breached"), rs.getBoolean("resolution_breached"), rs.getTimestamp("calculated_at").toInstant(), rs.getLong("version")), Math.max(1, Math.min(1000, limit))); }
    public void save(TicketSlaTarget t, Long expectedVersion) {
        if (expectedVersion == null) jdbc.update("INSERT INTO ticket_sla_target (ticket_id, policy_id, policy_name_snapshot, calendar_key_snapshot, response_due_at, resolution_due_at, first_responded_at, resolved_at, paused_seconds, pause_started_at, risk_level, response_breached, resolution_breached, calculated_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", t.ticketId(), t.policyId(), t.policyNameSnapshot(), t.calendarKeySnapshot(), ts(t.responseDueAt()), ts(t.resolutionDueAt()), ts(t.firstRespondedAt()), ts(t.resolvedAt()), t.pausedSeconds(), ts(t.pauseStartedAt()), t.riskLevel().name(), t.responseBreached(), t.resolutionBreached(), ts(t.calculatedAt()), t.version());
        else { int changed = jdbc.update("UPDATE ticket_sla_target SET response_due_at=?, resolution_due_at=?, first_responded_at=?, resolved_at=?, paused_seconds=?, pause_started_at=?, risk_level=?, response_breached=?, resolution_breached=?, calculated_at=?, version=? WHERE ticket_id=? AND version=?", ts(t.responseDueAt()), ts(t.resolutionDueAt()), ts(t.firstRespondedAt()), ts(t.resolvedAt()), t.pausedSeconds(), ts(t.pauseStartedAt()), t.riskLevel().name(), t.responseBreached(), t.resolutionBreached(), ts(t.calculatedAt()), t.version(), t.ticketId(), expectedVersion); if (changed != 1) throw new IllegalStateException("SLA target version conflict"); }
    }
    private static java.time.Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException { Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private static Timestamp ts(java.time.Instant value) { return value == null ? null : Timestamp.from(value); }
}
