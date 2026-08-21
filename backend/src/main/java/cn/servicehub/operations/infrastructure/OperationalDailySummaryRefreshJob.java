package cn.servicehub.operations.infrastructure;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes only today and yesterday, avoiding an unbounded aggregation of transactional history. */
@Component
@Profile("mysql")
public class OperationalDailySummaryRefreshJob {
    private final JdbcTemplate jdbc;
    public OperationalDailySummaryRefreshJob(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Scheduled(fixedDelayString = "${servicehub.operations.daily-refresh-delay-ms:300000}")
    public void refresh() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC); LocalDate from = today.minusDays(1); Instant now = Instant.now();
        jdbc.update("UPDATE operation_report_refresh_job SET last_started_at=?, last_status='RUNNING', last_error_code=NULL, updated_at=? WHERE job_key='daily-ticket-kpi'", java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        try {
            for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) refreshOne(date);
            jdbc.update("UPDATE operation_report_refresh_job SET last_succeeded_at=?, last_status='SUCCESS', refreshed_from=?, refreshed_to=?, updated_at=? WHERE job_key='daily-ticket-kpi'", java.sql.Timestamp.from(Instant.now()), Date.valueOf(from), Date.valueOf(today), java.sql.Timestamp.from(Instant.now()));
        } catch (RuntimeException error) {
            jdbc.update("UPDATE operation_report_refresh_job SET last_status='FAILED', last_error_code=?, updated_at=? WHERE job_key='daily-ticket-kpi'", error.getClass().getSimpleName().substring(0, Math.min(64, error.getClass().getSimpleName().length())), java.sql.Timestamp.from(Instant.now())); throw error;
        }
    }
    private void refreshOne(LocalDate day) {
        jdbc.update("DELETE FROM operation_daily_ticket_kpi WHERE business_date = ?", Date.valueOf(day));
        jdbc.update("""
            INSERT INTO operation_daily_ticket_kpi (business_date, requester_organization_id, ticket_status, ticket_volume, open_volume, response_seconds_sum, response_sample_count, resolution_seconds_sum, resolution_sample_count, at_risk_volume, breached_volume, refreshed_at)
            SELECT DATE(t.created_at), t.requester_organization_id, t.status, COUNT(*), SUM(CASE WHEN t.status NOT IN ('CLOSED','CANCELLED') THEN 1 ELSE 0 END),
                   COALESCE(SUM(CASE WHEN s.first_responded_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, t.created_at, s.first_responded_at) ELSE 0 END), 0), SUM(s.first_responded_at IS NOT NULL),
                   COALESCE(SUM(CASE WHEN s.resolved_at IS NOT NULL THEN TIMESTAMPDIFF(SECOND, t.created_at, s.resolved_at) ELSE 0 END), 0), SUM(s.resolved_at IS NOT NULL),
                   SUM(s.risk_level = 'AT_RISK'), SUM(s.risk_level = 'BREACHED'), UTC_TIMESTAMP(6)
              FROM ticket t LEFT JOIN ticket_sla_target s ON s.ticket_id = t.id
             WHERE t.created_at >= ? AND t.created_at < ?
             GROUP BY DATE(t.created_at), t.requester_organization_id, t.status
            """, Date.valueOf(day).toLocalDate().atStartOfDay(), Date.valueOf(day.plusDays(1)).toLocalDate().atStartOfDay());
    }
}
