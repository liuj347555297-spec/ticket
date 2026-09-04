package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.PlatformAuthorizationResolver;
import cn.servicehub.security.TicketAccessScopeResolver;
import cn.servicehub.ticket.application.TicketObjectContextResolver;
import cn.servicehub.ticket.domain.TicketAccessScope;
import cn.servicehub.ticket.domain.TicketObjectContext;
import cn.servicehub.workflow.application.ApprovalCandidateScopeResolver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ApprovalCandidateScopeResolverTest {
    private static final String TICKET_ID = "TKT-20260901-000001";
    private static final Set<String> ROLES = Set.of("ROLE_SERVICE_MANAGER");

    @Test
    void freezesOnlyIndependentCandidatesWhoseCurrentScopeCoversTheTicket() {
        var rolePool = Mockito.mock(IamRoleProjectionRepository.class);
        var authorizations = Mockito.mock(PlatformAuthorizationResolver.class);
        var scopes = Mockito.mock(TicketAccessScopeResolver.class);
        var contexts = Mockito.mock(TicketObjectContextResolver.class);
        var ticket = context();
        when(contexts.resolveForScope(TICKET_ID)).thenReturn(ticket);
        when(rolePool.findActiveIamUserIdsByRoleCodes(ROLES))
            .thenReturn(List.of("applicant", "beneficiary", "manager-in-scope", "manager-other-org"));

        CurrentUser inScope = current("manager-in-scope");
        CurrentUser otherOrganization = current("manager-other-org");
        when(authorizations.resolve("manager-in-scope", "APPROVAL_CANDIDATE")).thenReturn(inScope);
        when(authorizations.resolve("manager-other-org", "APPROVAL_CANDIDATE")).thenReturn(otherOrganization);
        when(scopes.resolve(inScope)).thenReturn(scope("manager-in-scope", "org-it"));
        when(scopes.resolve(otherOrganization)).thenReturn(scope("manager-other-org", "org-finance"));

        var resolver = new ApprovalCandidateScopeResolver(rolePool, authorizations, scopes, contexts);
        assertEquals(Set.of("manager-in-scope"), resolver.resolve(TICKET_ID, ROLES, "applicant", "beneficiary"));
        verify(authorizations, never()).resolve("applicant", "APPROVAL_CANDIDATE");
        verify(authorizations, never()).resolve("beneficiary", "APPROVAL_CANDIDATE");
    }

    @Test
    void currentRecheckRejectsSelfTargetAndCandidateWhoseScopeWasRevoked() {
        var rolePool = Mockito.mock(IamRoleProjectionRepository.class);
        var authorizations = Mockito.mock(PlatformAuthorizationResolver.class);
        var scopes = Mockito.mock(TicketAccessScopeResolver.class);
        var contexts = Mockito.mock(TicketObjectContextResolver.class);
        when(contexts.resolveForScope(TICKET_ID)).thenReturn(context());
        when(rolePool.findActiveIamUserIdsByRoleCodes(ROLES)).thenReturn(List.of("manager"));
        CurrentUser manager = current("manager");
        when(authorizations.resolve("manager", "APPROVAL_CANDIDATE")).thenReturn(manager);
        when(scopes.resolve(manager)).thenReturn(scope("manager", "org-it"), scope("manager", "org-revoked"));
        var resolver = new ApprovalCandidateScopeResolver(rolePool, authorizations, scopes, contexts);

        assertTrue(resolver.isCurrentlyEligible(TICKET_ID, ROLES, "manager", "applicant", "beneficiary"));
        assertFalse(resolver.isCurrentlyEligible(TICKET_ID, ROLES, "manager", "applicant", "beneficiary"));
        assertFalse(resolver.isCurrentlyEligible(TICKET_ID, ROLES, "applicant", "applicant", null));
        assertFalse(resolver.isCurrentlyEligible(TICKET_ID, ROLES, "beneficiary", "applicant", "beneficiary"));
    }

    private static CurrentUser current(String id) {
        return new CurrentUser(id, Set.of("ROLE_SERVICE_MANAGER"), "test", true, "scope-v1");
    }

    private static TicketAccessScope scope(String id, String organization) {
        return new TicketAccessScope(id, ROLES, Set.of(organization), Set.of("catalog-1"), Set.of(), Set.of(), true, false, false);
    }

    private static TicketObjectContext context() {
        return new TicketObjectContext("requester", "org-it", "catalog-1", null, Set.of(), false);
    }
}
