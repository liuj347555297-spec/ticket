package cn.servicehub.operations.infrastructure;

import cn.servicehub.operations.domain.DailyTicketKpiRow;
import cn.servicehub.operations.domain.OperationsReportRepository;
import cn.servicehub.operations.domain.QueueLoadRow;
import cn.servicehub.ticket.domain.TicketStatus;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL dashboards read only daily summary tables; current queue load is purposefully bounded. */
@Repository
@Profile("mysql")
public class MySqlOperationsReportRepository implements OperationsReportRepository {
    private final JdbcTemplate jdbc;
    public MySqlOperationsReportRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public List<DailyTicketKpiRow> findDaily(LocalDate from, LocalDate to, Set<String> organizations, boolean unrestricted) {
        StringBuilder sql = new StringBuilder("SELECT * FROM operation_daily_ticket_kpi WHERE business_date >= ? AND business_date <= ?"); List<Object> args = new ArrayList<>(List.of(Date.valueOf(from), Date.valueOf(to)));
        appendScope(sql, args, organizations, unrestricted, "requester_organization_id");
        return jdbc.query(sql.toString(), (rs, n) -> new DailyTicketKpiRow(rs.getDate("business_date").toLocalDate(), rs.getString("requester_organization_id"), TicketStatus.valueOf(rs.getString("ticket_status")), rs.getLong("ticket_volume"), rs.getLong("open_volume"), rs.getLong("response_seconds_sum"), rs.getLong("response_sample_count"), rs.getLong("resolution_seconds_sum"), rs.getLong("resolution_sample_count"), rs.getLong("at_risk_volume"), rs.getLong("breached_volume")), args.toArray());
    }
    public List<QueueLoadRow> findQueueLoad(Set<String> organizations, boolean unrestricted) {
        StringBuilder sql = new StringBuilder("SELECT COALESCE(w.primary_assignee_iam_user_id, 'UNASSIGNED') assignee, w.status, COUNT(*) total FROM ticket_workflow_instance w JOIN ticket t ON t.id = w.ticket_id WHERE w.status NOT IN ('CLOSED', 'CANCELLED')"); List<Object> args = new ArrayList<>(); appendScope(sql, args, organizations, unrestricted, "t.requester_organization_id"); sql.append(" GROUP BY w.primary_assignee_iam_user_id, w.status ORDER BY total DESC, assignee ASC LIMIT 200"); return jdbc.query(sql.toString(), (rs, n) -> new QueueLoadRow(rs.getString("assignee"), rs.getString("status"), rs.getLong("total")), args.toArray());
    }
    private static void appendScope(StringBuilder sql, List<Object> args, Set<String> organizations, boolean unrestricted, String column) { if (!unrestricted) { if (organizations.isEmpty()) { sql.append(" AND 1 = 0"); return; } sql.append(" AND ").append(column).append(" IN (").append("?,".repeat(organizations.size())); sql.setLength(sql.length() - 1); sql.append(")"); args.addAll(organizations); } }
}
