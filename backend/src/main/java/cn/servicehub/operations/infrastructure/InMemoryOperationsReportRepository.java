package cn.servicehub.operations.infrastructure;

import cn.servicehub.operations.domain.DailyTicketKpiRow;
import cn.servicehub.operations.domain.OperationsReportRepository;
import cn.servicehub.operations.domain.QueueLoadRow;
import cn.servicehub.sla.domain.SlaRiskLevel;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development-only aggregate over a small in-memory set. Production uses daily summary rows. */
@Repository
@Profile("!mysql")
public class InMemoryOperationsReportRepository implements OperationsReportRepository {
    private final TicketRepository tickets; private final TicketSlaTargetRepository targets;
    public InMemoryOperationsReportRepository(TicketRepository tickets, TicketSlaTargetRepository targets) { this.tickets = tickets; this.targets = targets; }
    public List<DailyTicketKpiRow> findDaily(LocalDate from, LocalDate to, Set<String> organizations, boolean unrestricted) {
        record Key(LocalDate day, String org, TicketStatus status) { }
        Map<Key, List<Ticket>> groups = tickets.findAll(new cn.servicehub.ticket.domain.TicketQuery(null, null, null)).stream().filter(t -> { LocalDate d = LocalDate.ofInstant(t.createdAt(), ZoneOffset.UTC); return !d.isBefore(from) && !d.isAfter(to) && (unrestricted || organizations.contains(t.requester().organizationId())); }).collect(Collectors.groupingBy(t -> new Key(LocalDate.ofInstant(t.createdAt(), ZoneOffset.UTC), t.requester().organizationId(), t.status())));
        return groups.entrySet().stream().map(entry -> { List<Ticket> values = entry.getValue(); long response = values.stream().map(t -> targets.findByTicketId(t.id()).orElse(null)).filter(java.util.Objects::nonNull).filter(t -> t.firstRespondedAt() != null).mapToLong(t -> Duration.between(t.calculatedAt(), t.firstRespondedAt()).toSeconds()).sum(); long responses = values.stream().filter(t -> targets.findByTicketId(t.id()).map(x -> x.firstRespondedAt() != null).orElse(false)).count(); long resolved = values.stream().map(t -> targets.findByTicketId(t.id()).orElse(null)).filter(java.util.Objects::nonNull).filter(t -> t.resolvedAt() != null).mapToLong(t -> Duration.between(t.calculatedAt(), t.resolvedAt()).toSeconds()).sum(); long resolvedCount = values.stream().filter(t -> targets.findByTicketId(t.id()).map(x -> x.resolvedAt() != null).orElse(false)).count(); return new DailyTicketKpiRow(entry.getKey().day(), entry.getKey().org(), entry.getKey().status(), values.size(), values.stream().filter(t -> !isTerminal(t.status())).count(), response, responses, resolved, resolvedCount, values.stream().filter(t -> targets.findByTicketId(t.id()).map(x -> x.riskLevel() == SlaRiskLevel.AT_RISK).orElse(false)).count(), values.stream().filter(t -> targets.findByTicketId(t.id()).map(x -> x.riskLevel() == SlaRiskLevel.BREACHED).orElse(false)).count()); }).toList();
    }
    public List<QueueLoadRow> findQueueLoad(Set<String> organizations, boolean unrestricted) { return List.of(); }
    private static boolean isTerminal(TicketStatus status) { return status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED; }
}
