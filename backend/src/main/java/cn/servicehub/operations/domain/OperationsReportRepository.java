package cn.servicehub.operations.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface OperationsReportRepository {
    List<DailyTicketKpiRow> findDaily(LocalDate from, LocalDate to, Set<String> organizationIds, boolean unrestricted);
    List<QueueLoadRow> findQueueLoad(Set<String> organizationIds, boolean unrestricted);
}
