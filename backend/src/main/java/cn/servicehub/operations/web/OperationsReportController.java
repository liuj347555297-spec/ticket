package cn.servicehub.operations.web;

import cn.servicehub.operations.application.OperationsKpi;
import cn.servicehub.operations.application.OperationsReportService;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/reports/operations", "/api/v1/operations"})
public class OperationsReportController {
    private final OperationsReportService reports;
    public OperationsReportController(OperationsReportService reports) { this.reports = reports; }
    @GetMapping("/kpis")
    OperationsKpiReport kpis(@RequestParam Instant from, @RequestParam Instant to) { return OperationsKpiReport.from(reports.kpi(day(from), day(to))); }
    @GetMapping("/trends")
    OperationsTrendReport trends(@RequestParam Instant from, @RequestParam Instant to,
                                 @RequestParam(defaultValue = "DAY") String granularity) {
        OperationsKpi kpi = reports.kpi(day(from), day(to));
        return new OperationsTrendReport(scope(from, to), "DAY".equals(granularity) ? "DAY" : "DAY", List.of(new OperationsTrendPoint(from, to, "PARTIAL", List.of(
            new ReportMetricValue("TICKET_CREATED_COUNT", (double) kpi.ticketVolume(), null, "PARTIAL"),
            new ReportMetricValue("TICKET_RESOLVED_COUNT", null, null, "NOT_AVAILABLE")))), freshness());
    }
    @GetMapping("/queue-load")
    OperationsQueueLoadReport queueLoad(@RequestParam Instant from, @RequestParam Instant to) {
        OperationsKpi kpi = reports.kpi(day(from), day(to));
        return new OperationsQueueLoadReport(scope(from, to), kpi.queueLoad().stream().map(item -> new OperationsQueueLoadItem(item.assigneeIamUserId(), item.assigneeIamUserId(), item.openCount(), 0, 0, 0)).toList(), freshness());
    }
    private static LocalDate day(Instant instant) { return instant.atZone(ZoneOffset.UTC).toLocalDate(); }
    private static ReportScope scope(Instant from, Instant to) { return new ReportScope("UTC", from, to, true, "当前 IAM 授权数据范围"); }
    private static ReportFreshness freshness() { return new ReportFreshness(Instant.now(), "PARTIAL"); }

    record OperationsKpiReport(ReportScope scope, List<ReportMetricDefinition> metricDefinitions, List<ReportMetricValue> metrics, ReportFreshness freshness) {
        static OperationsKpiReport from(OperationsKpi kpi) { return new OperationsKpiReport(new ReportScope("UTC", kpi.from().atStartOfDay().toInstant(ZoneOffset.UTC), kpi.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), true, "当前 IAM 授权数据范围"), List.of(), List.of(new ReportMetricValue("TICKET_CREATED_COUNT", (double) kpi.ticketVolume(), null, "COMPLETE"), new ReportMetricValue("OPEN_TICKET_COUNT", (double) kpi.queueLoad().stream().mapToLong(OperationsKpi.QueueLoad::openCount).sum(), null, "PARTIAL"), new ReportMetricValue("SLA_AT_RISK_COUNT", (double) kpi.atRiskCount(), null, "COMPLETE"), new ReportMetricValue("SLA_BREACHED_COUNT", (double) kpi.breachedCount(), null, "COMPLETE"), new ReportMetricValue("SLA_FIRST_RESPONSE_COMPLIANCE_RATE", null, null, "NOT_AVAILABLE"), new ReportMetricValue("SLA_RESOLUTION_COMPLIANCE_RATE", null, null, "NOT_AVAILABLE"), new ReportMetricValue("RESOLUTION_BUSINESS_MINUTES_P50", kpi.averageResolutionMinutes(), null, kpi.averageResolutionMinutes() == null ? "NOT_AVAILABLE" : "PARTIAL")) , OperationsReportController.freshness()); }
    }
    record OperationsTrendReport(ReportScope scope, String granularity, List<OperationsTrendPoint> points, ReportFreshness freshness) { }
    record OperationsTrendPoint(Instant bucketStart, Instant bucketEnd, String availability, List<ReportMetricValue> metrics) { }
    record OperationsQueueLoadReport(ReportScope scope, List<OperationsQueueLoadItem> items, ReportFreshness freshness) { }
    record OperationsQueueLoadItem(String queueCode, String queueName, long openTicketCount, long pendingAcceptanceCount, long atRiskCount, long breachedCount) { }
    record ReportMetricDefinition(String code, String name, String unit, String definition, String calculationBasis) { }
    record ReportMetricValue(String code, Double value, ReportMetricDimension dimension, String availability) { }
    record ReportMetricDimension(String ticketStatus, String ticketType) { }
    record ReportScope(String effectiveTimeZone, Instant from, Instant to, boolean dataScopeFiltered, String organizationScopeSummary) { }
    record ReportFreshness(Instant asOf, String missingDataStatus) { }
}
