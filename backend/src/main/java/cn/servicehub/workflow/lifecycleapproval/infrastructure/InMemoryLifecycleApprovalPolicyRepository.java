package cn.servicehub.workflow.lifecycleapproval.infrastructure;

import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import java.util.Comparator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryLifecycleApprovalPolicyRepository implements LifecycleApprovalPolicyRepository {
    private final CopyOnWriteArrayList<LifecycleApprovalPolicy> policies = new CopyOnWriteArrayList<>();
    public InMemoryLifecycleApprovalPolicyRepository() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        for (var action : cn.servicehub.workflow.domain.WorkflowAction.values()) {
            if (!java.util.Set.of("HOLD","ESCALATE","CANCEL","REOPEN","ASSIGN","ACCEPT","RESOLVE","CLOSE").contains(action.name())) continue;
            policies.add(new LifecycleApprovalPolicy("builtin-" + action.name(), "默认生命周期审批", action, null, null,
                java.util.Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN"), "ANY_ONE", 100, 1440,
                "DEFAULT-24H-V1", "AUDIT-ONLY-V1", "PUBLISHED", 1, now, now, now));
        }
    }
    @Override public List<LifecycleApprovalPolicy> findAll() { return policies.stream().sorted(Comparator.comparing(LifecycleApprovalPolicy::updatedAt).reversed()).toList(); }
    @Override public List<LifecycleApprovalPolicy> findPublishedByAction(String actionCode) { return policies.stream().filter(p -> "PUBLISHED".equals(p.status()) && p.action().name().equals(actionCode)).toList(); }
    @Override public Optional<LifecycleApprovalPolicy> findById(String id) { return policies.stream().filter(p -> p.id().equals(id)).findFirst(); }
    @Override public LifecycleApprovalPolicy save(LifecycleApprovalPolicy policy, Long expectedVersion) {
        if (expectedVersion == null) { policies.add(policy); return policy; }
        for (int i = 0; i < policies.size(); i++) if (policies.get(i).id().equals(policy.id()) && policies.get(i).version() == expectedVersion) { policies.set(i, policy); return policy; }
        throw new IllegalStateException("Lifecycle approval policy version conflict");
    }
}
