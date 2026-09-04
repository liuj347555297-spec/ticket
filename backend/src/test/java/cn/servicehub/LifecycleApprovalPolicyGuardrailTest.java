package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.application.LifecycleApprovalPolicyService;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LifecycleApprovalPolicyGuardrailTest {
    @Test void acceptsOnlyWhitelistedCandidateRolesAndRejectsAmbiguousPublishedScope() {
        MemoryPolicies policies = new MemoryPolicies();
        ServiceCatalogRepository catalog = Mockito.mock(ServiceCatalogRepository.class);
        CurrentUserProvider users = new CurrentUserProvider() {
            private final CurrentUser admin = new CurrentUser("admin", Set.of("ROLE_PLATFORM_ADMIN"), "test");
            @Override public Optional<CurrentUser> currentUser() { return Optional.of(admin); }
            @Override public CurrentUser requireCurrentUser() { return admin; }
        };
        AuditEventPublisher audit = event -> { };
        LifecycleApprovalPolicyService service = new LifecycleApprovalPolicyService(policies, catalog, users, audit);

        assertThrows(IllegalArgumentException.class, () -> service.create(command(Set.of("ROLE_AUDITOR"))));
        LifecycleApprovalPolicy first = service.create(command(Set.of("ROLE_SERVICE_MANAGER")));
        LifecycleApprovalPolicy published = service.publish(first.id(), first.version());
        LifecycleApprovalPolicy second = service.create(command(Set.of("ROLE_PLATFORM_ADMIN")));
        assertThrows(IllegalStateException.class, () -> service.publish(second.id(), second.version()));
        assertEquals("PUBLISHED", published.status());
    }

    private static LifecycleApprovalPolicyService.PolicyCommand command(Set<String> roles) {
        return new LifecycleApprovalPolicyService.PolicyCommand("hold", WorkflowAction.HOLD, null, null, roles,
            "ANY_ONE", 100, 60, "TIMEOUT-V1", "ESCALATION-V1", null);
    }

    private static final class MemoryPolicies implements LifecycleApprovalPolicyRepository {
        private final List<LifecycleApprovalPolicy> rows = new ArrayList<>();
        @Override public List<LifecycleApprovalPolicy> findAll() { return List.copyOf(rows); }
        @Override public List<LifecycleApprovalPolicy> findPublishedByAction(String action) { return rows.stream().filter(p -> p.status().equals("PUBLISHED") && p.action().name().equals(action)).toList(); }
        @Override public Optional<LifecycleApprovalPolicy> findById(String id) { return rows.stream().filter(p -> p.id().equals(id)).findFirst(); }
        @Override public LifecycleApprovalPolicy save(LifecycleApprovalPolicy policy, Long expected) { if (expected == null) { rows.add(policy); return policy; } for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(policy.id()) && rows.get(i).version() == expected) { rows.set(i, policy); return policy; } throw new IllegalStateException("conflict"); }
    }
}
