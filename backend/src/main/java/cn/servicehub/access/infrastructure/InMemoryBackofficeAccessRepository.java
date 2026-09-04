package cn.servicehub.access.infrastructure;

import cn.servicehub.access.application.BackofficeAccessConflictException;
import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Mutable development/test equivalent of the MySQL access-authorization tables. */
@Repository
@Profile("!mysql")
public class InMemoryBackofficeAccessRepository implements BackofficeAccessRepository {
    private final Map<String, BackofficeAccess> values = new ConcurrentHashMap<>();

    public InMemoryBackofficeAccessRepository() {
        Instant seeded = Instant.parse("2026-01-01T00:00:00Z");
        put("iam-u-1002", Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SERVICE_MANAGER"),
            Set.of(new BackofficeDataScope("ORGANIZATION", "org-finance"), new BackofficeDataScope("SERVICE_CATALOG", "SC-browser-performance")), seeded);
        put("iam-u-local-first-line", Set.of("ROLE_FIRST_LINE_SUPPORT"),
            Set.of(new BackofficeDataScope("ORGANIZATION", "ORG-LOCAL-IT")), seeded);
        put("iam-u-local-service-manager", Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SERVICE_MANAGER"),
            Set.of(new BackofficeDataScope("ORGANIZATION", "ORG-LOCAL-IT")), seeded);
        put("iam-u-local-admin", Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR"),
            Set.of(new BackofficeDataScope("ORGANIZATION", "ORG-LOCAL-IT")), seeded);
    }

    @Override public Optional<BackofficeAccess> findByIamUserId(String iamUserId) { return Optional.ofNullable(values.get(iamUserId)); }
    @Override public List<String> findEnabledIamUserIdsByRoleCodes(Set<String> roles) {
        return values.values().stream().filter(BackofficeAccess::enabled).filter(value -> value.roleCodes().stream().anyMatch(roles::contains))
            .map(BackofficeAccess::iamUserId).sorted().toList();
    }
    @Override public long countEnabledUsersWithRole(String roleCode) { return values.values().stream().filter(BackofficeAccess::enabled).filter(value -> value.roleCodes().contains(roleCode)).count(); }
    @Override public synchronized BackofficeAccess save(BackofficeAccess access, long expectedVersion, String actorIamUserId) {
        BackofficeAccess old = values.get(access.iamUserId());
        if ((old == null ? 0 : old.version()) != expectedVersion) throw new BackofficeAccessConflictException();
        values.put(access.iamUserId(), access);
        return access;
    }
    private void put(String iamUserId, Set<String> roles, Set<BackofficeDataScope> scopes, Instant now) {
        values.put(iamUserId, new BackofficeAccess(iamUserId, true, roles, scopes, 1, now));
    }
}
