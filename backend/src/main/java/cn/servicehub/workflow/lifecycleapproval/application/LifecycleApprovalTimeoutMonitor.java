package cn.servicehub.workflow.lifecycleapproval.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleActionApprovalRepository;
import cn.servicehub.workflow.lifecycleapproval.engine.LifecycleActionApprovalEnginePort;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Timeout is a safe closure, never an implied approval.  The CAS claim makes an in-flight human
 * decision lose safely before its Flowable instance is cancelled; escalation policy is recorded
 * as immutable evidence for an operator queue/auditor, not executed as a privileged ticket action.
 */
@Component
public class LifecycleApprovalTimeoutMonitor {
    private final LifecycleActionApprovalRepository requests; private final LifecycleActionApprovalEnginePort engine;
    private final AuditEventPublisher audit; private final Clock clock = Clock.systemUTC();
    public LifecycleApprovalTimeoutMonitor(LifecycleActionApprovalRepository requests, LifecycleActionApprovalEnginePort engine, AuditEventPublisher audit) { this.requests=requests; this.engine=engine; this.audit=audit; }
    @Scheduled(fixedDelayString="${servicehub.workflow.lifecycle-approval.timeout-scan-delay-ms:60000}")
    public void scan() { expireDue(100); }
    @Transactional
    public int expireDue(int limit) {
        Instant now=clock.instant(); int expired=0;
        for (var request : requests.findPendingDueBefore(now, Math.max(1, Math.min(limit, 500)))) {
            if (!requests.claimExpiration(request.ticketId(), request.id(), now)) continue;
            try {
                engine.cancelExpired(request.approvalEngineInstanceId());
                if (!requests.markExpired(request.ticketId(), request.id(), now)) continue;
                requests.appendTimeoutEvent(UUID.randomUUID().toString(), request, now);
                audit.publish(new AuditEvent(now, "system", "system", "LIFECYCLE_APPROVAL_EXPIRED", "ticket", request.ticketId(),
                    Map.of("approvalRequestId", request.id(), "action", request.action().name(), "timeoutPolicyVersion", request.timeoutPolicyVersion(), "escalationPolicyVersion", request.escalationPolicyVersion())));
                expired++;
            } catch (RuntimeException ignored) {
                // Claim remains EXPIRING on an infrastructure failure. It cannot be approved or
                // executed accidentally and is deliberately visible for recovery/audit.
            }
        }
        return expired;
    }
}
