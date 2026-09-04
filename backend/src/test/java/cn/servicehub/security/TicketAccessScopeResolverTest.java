package cn.servicehub.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.servicehub.config.SecurityProperties;
import cn.servicehub.ticket.application.TicketPaginationProperties;
import cn.servicehub.ticket.domain.TicketObjectContext;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class TicketAccessScopeResolverTest {
    private final TicketAccessScopeResolver resolver = new TicketAccessScopeResolver(new SecurityProperties(List.of(), true), new MockEnvironment());

    @Test
    void unionsSameTypeAndIntersectsDifferentTypes() {
        CurrentUser user = new CurrentUser("support", Set.of("ROLE_FIRST_LINE_SUPPORT",
            "DATA_SCOPE_ORGANIZATION:org-a", "DATA_SCOPE_ORGANIZATION:org-b",
            "DATA_SCOPE_SERVICE:catalog-a"), "OIDC", true, "scope-v1");
        var scope = resolver.resolve(user);
        assertTrue(scope.allowsScoped(context("org-a", "catalog-a")));
        assertTrue(scope.allowsScoped(context("org-b", "catalog-a")));
        assertFalse(scope.allowsScoped(context("org-a", "catalog-b")));
        assertFalse(scope.allowsScoped(context("org-c", "catalog-a")));
    }

    @Test
    void queueScopeFailsClosedUntilQueueSnapshotExists() {
        CurrentUser user = new CurrentUser("support", Set.of("ROLE_FIRST_LINE_SUPPORT",
            "DATA_SCOPE_ORGANIZATION:org-a", "DATA_SCOPE_QUEUE:queue-a"), "OIDC", true, "scope-v1");
        assertFalse(resolver.resolve(user).allowsScoped(context("org-a", "catalog-a")));
    }

    @Test
    void cursorSigningKeyRequiresAtLeastThirtyTwoBytes() {
        assertThrows(IllegalArgumentException.class, () -> new TicketPaginationProperties("too-short", Duration.ofMinutes(15)));
        new TicketPaginationProperties("01234567890123456789012345678901", Duration.ofMinutes(15));
    }

    @Test
    void productionProfileRejectsDirectTestIdentityEvenIfFlagIsSet() {
        MockEnvironment production = new MockEnvironment(); production.setActiveProfiles("prod");
        TicketAccessScopeResolver productionResolver = new TicketAccessScopeResolver(new SecurityProperties(List.of(), true), production);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
            () -> productionResolver.resolve(new CurrentUser("direct", Set.of("ROLE_PLATFORM_ADMIN"), "test")));
    }

    private TicketObjectContext context(String organization, String catalog) {
        return new TicketObjectContext("requester", organization, catalog, null, Set.of(), false);
    }
}
