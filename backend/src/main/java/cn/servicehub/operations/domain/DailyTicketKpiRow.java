package cn.servicehub.operations.domain;

import cn.servicehub.ticket.domain.TicketStatus;
import java.time.LocalDate;

/** Pre-aggregated row; a dashboard never needs to scan the full ticket event history. */
public record DailyTicketKpiRow(LocalDate businessDate, String organizationId, TicketStatus status, long volume,
                                long openVolume, long responseSecondsSum, long responseSamples,
                                long resolutionSecondsSum, long resolutionSamples, long atRiskVolume, long breachedVolume) {
}
