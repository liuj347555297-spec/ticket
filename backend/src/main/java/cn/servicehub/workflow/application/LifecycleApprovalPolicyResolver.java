package cn.servicehub.workflow.application;

import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Selects a published policy by server-owned ticket attributes and freezes its effective voter set. */
@Component
public class LifecycleApprovalPolicyResolver {
    private final LifecycleApprovalPolicyRepository policies; private final ApprovalCandidateScopeResolver candidates;
    public LifecycleApprovalPolicyResolver(LifecycleApprovalPolicyRepository policies, ApprovalCandidateScopeResolver candidates) { this.policies = policies; this.candidates = candidates; }
    public Optional<LifecycleApprovalPolicy> findApplicable(Ticket ticket, WorkflowAction action) {
        return policies.findPublishedByAction(action.name()).stream().filter(p -> matches(p, ticket))
            .max(Comparator.comparingInt(this::specificity).thenComparing(LifecycleApprovalPolicy::publishedAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(LifecycleApprovalPolicy::version).thenComparing(LifecycleApprovalPolicy::id));
    }
    public Resolved resolve(Ticket ticket, WorkflowAction action, String applicantIamUserId, String targetIamUserId, Instant now) {
        LifecycleApprovalPolicy policy = findApplicable(ticket, action).orElseThrow(WorkflowStateException::new);
        Set<String> resolvedCandidates = candidates.resolve(ticket.id(), policy.candidateRoles(), applicantIamUserId, targetIamUserId);
        if (resolvedCandidates.isEmpty()) throw new WorkflowStateException();
        int required = switch (policy.decisionMode()) {
            case "ANY_ONE" -> 1;
            case "ALL_OF" -> resolvedCandidates.size();
            case "QUORUM" -> Math.max(1, (int) Math.ceil(resolvedCandidates.size() * policy.approvalThresholdPercent() / 100.0));
            default -> throw new WorkflowStateException();
        };
        return new Resolved(policy, resolvedCandidates, required, now.plusSeconds(policy.timeoutMinutes() * 60L));
    }
    private boolean matches(LifecycleApprovalPolicy p, Ticket t) { return (p.serviceCatalogItemId() == null || p.serviceCatalogItemId().equals(t.serviceCatalogItem().id())) && (p.priority() == null || p.priority() == t.priority()); }
    private int specificity(LifecycleApprovalPolicy p) { return (p.serviceCatalogItemId()==null?0:2)+(p.priority()==null?0:1); }
    public record Resolved(LifecycleApprovalPolicy policy, Set<String> candidateIamUserIds, int requiredApprovalCount, Instant dueAt) { }
}
