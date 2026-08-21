package cn.servicehub.operations.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record OperationsKpi(LocalDate from, LocalDate to, long ticketVolume, Map<String, Long> statusDistribution,
                            Double averageResponseMinutes, Double averageResolutionMinutes, long atRiskCount,
                            long breachedCount, List<QueueLoad> queueLoad, boolean dailySummaryFresh) {
    public OperationsKpi { statusDistribution = statusDistribution == null ? Map.of() : Map.copyOf(statusDistribution); queueLoad = queueLoad == null ? List.of() : List.copyOf(queueLoad); }
    public record QueueLoad(String assigneeIamUserId, String queueState, long openCount) { }
}
