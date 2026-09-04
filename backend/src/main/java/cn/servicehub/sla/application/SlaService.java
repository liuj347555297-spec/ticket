package cn.servicehub.sla.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.sla.domain.SlaPolicy;
import cn.servicehub.sla.domain.SlaPolicyRepository;
import cn.servicehub.sla.domain.SlaRiskLevel;
import cn.servicehub.sla.domain.TicketSlaTarget;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calculates only. Escalation, penalties and any external notification stay opt-in future capabilities. */
@Service
public class SlaService {
    private static final String FALLBACK_POLICY_ID = "00000000-0000-4000-8000-000000000007";
    private static final Set<String> POLICY_WRITE_ROLES = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_SERVICE_MANAGER", "ROLE_SLA_MANAGER");
    private static final Set<String> POLICY_READ_ROLES = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_SERVICE_MANAGER", "ROLE_SLA_MANAGER", "ROLE_AUDITOR");
    private final SlaPolicyRepository policies;
    private final TicketSlaTargetRepository targets;
    private final WorkCalendarService calendars;
    private final CurrentUserProvider users;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();

    public SlaService(SlaPolicyRepository policies, TicketSlaTargetRepository targets, WorkCalendarService calendars, CurrentUserProvider users, AuditEventPublisher audit) {
        this.policies = policies; this.targets = targets; this.calendars = calendars; this.users = users; this.audit = audit;
    }

    public TicketSlaTarget onTicketCreated(Ticket ticket) {
        Instant now = clock.instant();
        SlaPolicy policy = selectPolicy(ticket);
        String calendarSnapshot = calendars.current(policy.calendarKey()).snapshotKey();
        TicketSlaTarget target = assess(new TicketSlaTarget(ticket.id(), policy.id(), policy.name(), calendarSnapshot,
            calendars.addBusinessMinutes(calendarSnapshot, now, policy.responseTargetMinutes()), calendars.addBusinessMinutes(calendarSnapshot, now, policy.resolutionTargetMinutes()),
            null, null, 0, policy.pauseStatuses().contains(ticket.status()) ? now : null, SlaRiskLevel.ON_TRACK,
            false, false, now, 0), now);
        targets.save(target, null);
        audit("SLA_TARGET_CALCULATED", ticket.id(), Map.of("policyId", policy.id(), "risk", target.riskLevel().name()));
        return target;
    }

    public void onTicketStateChanged(Ticket before, Ticket after) {
        TicketSlaTarget current = targets.findByTicketId(after.id()).orElseGet(() -> onTicketCreated(before));
        SlaPolicy policy = policies.findById(current.policyId()).orElseGet(() -> selectPolicy(after));
        Instant now = clock.instant();
        long paused = current.pausedSeconds();
        Instant pauseStarted = current.pauseStartedAt();
        if (!policy.pauseStatuses().contains(before.status()) && policy.pauseStatuses().contains(after.status())) pauseStarted = now;
        Instant responseDue=current.responseDueAt(), resolutionDue=current.resolutionDueAt();
        if (policy.pauseStatuses().contains(before.status()) && !policy.pauseStatuses().contains(after.status()) && pauseStarted != null) {
            long pausedBusinessSeconds=calendars.businessSecondsBetween(current.calendarKeySnapshot(), pauseStarted, now);
            paused += Math.max(0, Duration.between(pauseStarted, now).toSeconds());
            responseDue=calendars.addBusinessSeconds(current.calendarKeySnapshot(), responseDue, pausedBusinessSeconds);
            resolutionDue=calendars.addBusinessSeconds(current.calendarKeySnapshot(), resolutionDue, pausedBusinessSeconds);
            pauseStarted = null;
        }
        Instant firstResponded = current.firstRespondedAt() == null && isResponseState(after.status()) ? now : current.firstRespondedAt();
        Instant resolved = current.resolvedAt() == null && after.status() == TicketStatus.RESOLVED ? now : current.resolvedAt();
        TicketSlaTarget next = assess(new TicketSlaTarget(after.id(), current.policyId(), current.policyNameSnapshot(), current.calendarKeySnapshot(),
            responseDue, resolutionDue, firstResponded, resolved, paused, pauseStarted, current.riskLevel(),
            current.responseBreached(), current.resolutionBreached(), current.calculatedAt(), current.version() + 1), now);
        targets.save(next, current.version());
        if (next.riskLevel() != current.riskLevel()) audit("SLA_RISK_CHANGED", after.id(), Map.of("from", current.riskLevel().name(), "to", next.riskLevel().name()));
    }

    public TicketSlaTarget get(String ticketId) { return targets.findByTicketId(ticketId).orElseThrow(() -> new IllegalArgumentException("SLA target not found")); }

    /** Scheduler entry point: recalculates target risk only, never mutates ticket lifecycle state. */
    @Transactional
    public SlaRiskTransition refreshRisk(TicketSlaTarget current) {
        Instant now = clock.instant();
        TicketSlaTarget next = assess(new TicketSlaTarget(current.ticketId(), current.policyId(), current.policyNameSnapshot(), current.calendarKeySnapshot(), current.responseDueAt(), current.resolutionDueAt(), current.firstRespondedAt(), current.resolvedAt(), current.pausedSeconds(), current.pauseStartedAt(), current.riskLevel(), current.responseBreached(), current.resolutionBreached(), current.calculatedAt(), current.version() + 1), now);
        if (next.riskLevel() == current.riskLevel() && next.responseBreached() == current.responseBreached() && next.resolutionBreached() == current.resolutionBreached()) return null;
        targets.save(next, current.version());
        audit("SLA_RISK_CHANGED", current.ticketId(), Map.of("from", current.riskLevel().name(), "to", next.riskLevel().name()));
        return new SlaRiskTransition(current.ticketId(), next.version(), current.riskLevel(), next.riskLevel());
    }

    public java.util.List<SlaPolicy> listPolicies() {
        CurrentUser actor = users.requireCurrentUser();
        if (actor.authorities().stream().noneMatch(POLICY_READ_ROLES::contains)) throw new AccessDeniedException("SLA policy administration is not authorized");
        return policies.findAll();
    }

    @Transactional
    public SlaPolicy savePolicy(String policyId, SlaPolicyCommand command) {
        CurrentUser actor = users.requireCurrentUser();
        if (actor.authorities().stream().noneMatch(POLICY_WRITE_ROLES::contains)) throw new AccessDeniedException("SLA policy administration is not authorized");
        requireWritableScope(actor, command);
        Instant now = clock.instant();
        SlaPolicy existing = policyId == null ? null : policies.findById(policyId).orElseThrow(() -> new IllegalArgumentException("SLA policy not found"));
        if (existing != null && (command.expectedVersion() == null || command.expectedVersion() != existing.version())) throw new IllegalStateException("SLA policy version conflict");
        String calendarKey = clean(command.calendarKey(), 64);
        calendars.current(calendarKey);
        SlaPolicy saved = policies.save(new SlaPolicy(existing == null ? UUID.randomUUID().toString() : existing.id(), clean(command.name(), 120),
            blankToNull(command.serviceCatalogItemId()), command.priority(), blankToNull(command.organizationScopeId()), command.responseTargetMinutes(),
            command.resolutionTargetMinutes(), calendarKey, command.pauseStatuses(), command.active(),
            existing == null ? 0 : existing.version() + 1, existing == null ? now : existing.createdAt(), now), existing == null ? null : existing.version());
        audit("SLA_POLICY_" + (existing == null ? "CREATED" : "UPDATED"), saved.id(), Map.of("active", Boolean.toString(saved.active()), "version", Long.toString(saved.version())));
        return saved;
    }

    private SlaPolicy selectPolicy(Ticket ticket) {
        return policies.findActive().stream().filter(policy -> matches(policy, ticket))
            .max(Comparator.comparingInt(policy -> specificity(policy))).orElseGet(() -> fallback(ticket));
    }
    private boolean matches(SlaPolicy policy, Ticket ticket) {
        return (policy.serviceCatalogItemId() == null || policy.serviceCatalogItemId().equals(ticket.serviceCatalogItem().id()))
            && (policy.priority() == null || policy.priority() == ticket.priority())
            && (policy.organizationScopeId() == null || policy.organizationScopeId().equals(ticket.requester().organizationId()));
    }
    private int specificity(SlaPolicy policy) { return (policy.serviceCatalogItemId() == null ? 0 : 4) + (policy.priority() == null ? 0 : 2) + (policy.organizationScopeId() == null ? 0 : 1); }
    private SlaPolicy fallback(Ticket ticket) { Instant now = clock.instant(); return new SlaPolicy(FALLBACK_POLICY_ID, "默认服务台", null, ticket.priority(), null, 60, 480, "24X7", Set.of(TicketStatus.ON_HOLD, TicketStatus.PENDING_USER_FEEDBACK), true, 0, now, now); }
    private TicketSlaTarget assess(TicketSlaTarget target, Instant now) {
        Instant responseDue = target.responseDueAt();
        Instant resolutionDue = target.resolutionDueAt();
        boolean responseBreach = target.firstRespondedAt() == null && now.isAfter(responseDue);
        boolean resolutionBreach = target.resolvedAt() == null && now.isAfter(resolutionDue);
        boolean atRisk = !responseBreach && !resolutionBreach && (target.firstRespondedAt() == null && atLeastEightyPercent(now, responseDue, target.calculatedAt()) || target.resolvedAt() == null && atLeastEightyPercent(now, resolutionDue, target.calculatedAt()));
        return new TicketSlaTarget(target.ticketId(), target.policyId(), target.policyNameSnapshot(), target.calendarKeySnapshot(), target.responseDueAt(), target.resolutionDueAt(), target.firstRespondedAt(), target.resolvedAt(), target.pausedSeconds(), target.pauseStartedAt(), (responseBreach || resolutionBreach) ? SlaRiskLevel.BREACHED : atRisk ? SlaRiskLevel.AT_RISK : SlaRiskLevel.ON_TRACK, responseBreach, resolutionBreach, target.calculatedAt(), target.version());
    }
    private boolean atLeastEightyPercent(Instant now, Instant due, Instant base) { long whole = Math.max(1, Duration.between(base, due).toSeconds()); return Duration.between(base, now).toSeconds() * 100 >= whole * 80; }
    private boolean isResponseState(TicketStatus status) { return status == TicketStatus.PENDING_ACCEPTANCE || status == TicketStatus.IN_PROGRESS || status == TicketStatus.PENDING_USER_FEEDBACK || status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED; }
    private String clean(String value, int max) { if (value == null || value.isBlank() || value.trim().length() > max) throw new IllegalArgumentException("SLA policy value is invalid"); return value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    /**
     * A service/SLA manager is not a global configuration administrator.  The target scope must
     * be carried by the IAM assertion itself; an empty scope would otherwise turn a branch rule
     * into an all-organization rule.  Platform administrators remain the narrowly defined
     * exception and are still fully audited.
     */
    private void requireWritableScope(CurrentUser actor, SlaPolicyCommand command) {
        if (actor.authorities().contains("ROLE_PLATFORM_ADMIN")) return;
        String organizationScope = blankToNull(command.organizationScopeId());
        String serviceScope = blankToNull(command.serviceCatalogItemId());
        if (organizationScope == null || serviceScope == null
            || !actor.authorities().contains("DATA_SCOPE_ORGANIZATION:" + organizationScope)
            || !actor.authorities().contains("DATA_SCOPE_SERVICE:" + serviceScope)) {
            audit("SLA_POLICY_WRITE_DENIED", "collection", Map.of("reason", "OUT_OF_SCOPE"));
            throw new AccessDeniedException("SLA policy scope is not authorized");
        }
    }
    private void audit(String action, String id, Map<String, String> details) { CurrentUser actor = users.currentUser().orElse(new CurrentUser("system", Set.of(), "system")); audit.publish(new AuditEvent(clock.instant(), MDC.get("requestId") == null ? "system" : MDC.get("requestId"), actor.iamUserId(), action, "sla", id, details)); }
    public record SlaRiskTransition(String ticketId, long targetVersion, SlaRiskLevel from, SlaRiskLevel to) { }
}
