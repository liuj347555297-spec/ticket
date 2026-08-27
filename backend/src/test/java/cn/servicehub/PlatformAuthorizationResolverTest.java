package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeDataScope;
import cn.servicehub.access.infrastructure.InMemoryBackofficeAccessRepository;
import cn.servicehub.iam.infrastructure.InMemoryIamUserProjectionRepository;
import cn.servicehub.security.PlatformAuthorizationResolver;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlatformAuthorizationResolverTest {
    @Test
    void verifiedIamSubjectStartsAsRequesterAndGetsOnlyEnabledLocalBackofficeGrant() {
        InMemoryBackofficeAccessRepository access = new InMemoryBackofficeAccessRepository();
        PlatformAuthorizationResolver resolver = new PlatformAuthorizationResolver(new InMemoryIamUserProjectionRepository(), access);
        assertEquals(Set.of("ROLE_REQUESTER"), resolver.resolve("iam-u-1001", "OIDC").authorities());
        access.save(new BackofficeAccess("iam-u-1001", true, Set.of("ROLE_FIRST_LINE_SUPPORT"),
            Set.of(new BackofficeDataScope("QUEUE", "QUEUE-DESK-01")), 1, Instant.now()), 0, "iam-u-local-admin");
        var resolved = resolver.resolve("iam-u-1001", "OIDC");
        assertTrue(resolved.authorities().contains("ROLE_REQUESTER"));
        assertTrue(resolved.authorities().contains("ROLE_FIRST_LINE_SUPPORT"));
        assertTrue(resolved.authorities().contains("DATA_SCOPE_QUEUE:QUEUE-DESK-01"));
        access.save(new BackofficeAccess("iam-u-1001", false, Set.of("ROLE_FIRST_LINE_SUPPORT"), Set.of(), 2, Instant.now()), 1, "iam-u-local-admin");
        assertFalse(resolver.resolve("iam-u-1001", "OIDC").authorities().contains("ROLE_FIRST_LINE_SUPPORT"));
    }
}
