package cn.servicehub.sla.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.sla.domain.SlaEscalationEventRepository;
import cn.servicehub.sla.domain.SlaRiskLevel;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import java.time.Instant;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Emits a durable, idempotent escalation signal only. A separate governance-approved consumer may start a workflow. */
@Component
public class SlaRiskMonitor {
    private final TicketSlaTargetRepository targets; private final SlaService sla; private final SlaEscalationEventRepository events; private final AuditEventPublisher audit;
    public SlaRiskMonitor(TicketSlaTargetRepository targets,SlaService sla,SlaEscalationEventRepository events,AuditEventPublisher audit) { this.targets=targets;this.sla=sla;this.events=events;this.audit=audit; }
    @Scheduled(fixedDelayString="${servicehub.sla.risk-scan-delay-ms:60000}")
    public void scan() { for(var target:targets.findOpenTargets(1000)) { try { var transition=sla.refreshRisk(target); if(transition==null || transition.to()==SlaRiskLevel.ON_TRACK) continue; String code=transition.to()==SlaRiskLevel.BREACHED?"SLA_BREACHED":"SLA_WARNING"; if(events.appendIfAbsent(transition.ticketId(),transition.targetVersion(),code,transition.to(),Instant.now())) audit.publish(new AuditEvent(Instant.now(),"system","system",code,"ticket",transition.ticketId(),Map.of("targetVersion",String.valueOf(transition.targetVersion()),"mode","EVENT_ONLY"))); } catch(IllegalStateException ignored) { /* optimistic race: next bounded scan reloads it */ } } }
}
