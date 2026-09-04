package cn.servicehub.operations.application;

import cn.servicehub.operations.domain.DailyTicketKpiRow;
import cn.servicehub.operations.domain.OperationsReportRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Read-only reporting service. Date ranges are capped before any repository query is made. */
@Service
public class OperationsReportService {
    private static final Set<String> REPORT_ROLES = Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR", "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT");
    private final OperationsReportRepository reports; private final CurrentUserProvider users;
    private final OperationsAuthorizationScopeResolver scopes;
    public OperationsReportService(OperationsReportRepository reports, CurrentUserProvider users,
                                   OperationsAuthorizationScopeResolver scopes) {
        this.reports = reports; this.users = users; this.scopes = scopes;
    }
    public OperationsKpi kpi(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(30).isBefore(to)) throw new IllegalArgumentException("Report date range must be between 1 and 31 days");
        CurrentUser user = users.requireCurrentUser();
        if (user.authorities().stream().noneMatch(REPORT_ROLES::contains)) throw new AccessDeniedException("Operational reporting is not authorized");
        Set<String> organizationScope = scopes.organizations(user);
        List<DailyTicketKpiRow> rows = reports.findDaily(from, to, organizationScope, false);
        long volume = rows.stream().mapToLong(DailyTicketKpiRow::volume).sum();
        long responseSamples = rows.stream().mapToLong(DailyTicketKpiRow::responseSamples).sum();
        long resolutionSamples = rows.stream().mapToLong(DailyTicketKpiRow::resolutionSamples).sum();
        var states = rows.stream().collect(Collectors.groupingBy(row -> row.status().name(), LinkedHashMap::new, Collectors.summingLong(DailyTicketKpiRow::volume)));
        return new OperationsKpi(from, to, volume, states, averageMinutes(rows.stream().mapToLong(DailyTicketKpiRow::responseSecondsSum).sum(), responseSamples), averageMinutes(rows.stream().mapToLong(DailyTicketKpiRow::resolutionSecondsSum).sum(), resolutionSamples), rows.stream().mapToLong(DailyTicketKpiRow::atRiskVolume).sum(), rows.stream().mapToLong(DailyTicketKpiRow::breachedVolume).sum(), reports.findQueueLoad(organizationScope, false).stream().map(row -> new OperationsKpi.QueueLoad(row.assigneeIamUserId(), row.queueState(), row.openCount())).toList(), true);
    }
    private static Double averageMinutes(long seconds, long samples) { return samples == 0 ? null : Math.round((seconds * 100.0 / samples / 60.0)) / 100.0; }
}
