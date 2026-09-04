package cn.servicehub.workflow.application;

import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import cn.servicehub.security.PlatformAuthorizationResolver;
import cn.servicehub.security.TicketAccessScopeResolver;
import cn.servicehub.ticket.application.TicketObjectContextResolver;
import cn.servicehub.ticket.domain.TicketObjectContext;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Resolves approval candidates from current role membership and the target ticket's server-owned scope. */
@Component
public class ApprovalCandidateScopeResolver {
    private final IamRoleProjectionRepository roles;
    private final PlatformAuthorizationResolver authorizations;
    private final TicketAccessScopeResolver ticketScopes;
    private final TicketObjectContextResolver ticketContexts;

    public ApprovalCandidateScopeResolver(IamRoleProjectionRepository roles,
                                           PlatformAuthorizationResolver authorizations,
                                           TicketAccessScopeResolver ticketScopes,
                                           TicketObjectContextResolver ticketContexts) {
        this.roles = roles;
        this.authorizations = authorizations;
        this.ticketScopes = ticketScopes;
        this.ticketContexts = ticketContexts;
    }

    /** Creation-stage resolution. The returned set is the only set that may be frozen into the approval. */
    public Set<String> resolve(String ticketId, Set<String> candidateRoles,
                               String applicantIamUserId, String beneficiaryIamUserId) {
        if (ticketId == null || candidateRoles == null || candidateRoles.isEmpty()) return Set.of();
        TicketObjectContext context = ticketContexts.resolveForScope(ticketId);
        Set<String> candidates = new TreeSet<>();
        for (String candidate : roles.findActiveIamUserIdsByRoleCodes(candidateRoles)) {
            if (excluded(candidate, applicantIamUserId, beneficiaryIamUserId)) continue;
            if (eligible(candidate, candidateRoles, context)) candidates.add(candidate);
        }
        return Set.copyOf(candidates);
    }

    /** Display/decision-stage recheck. It never adds a user to a previously frozen candidate set. */
    public boolean isCurrentlyEligible(String ticketId, Set<String> candidateRoles, String candidateIamUserId,
                                       String applicantIamUserId, String beneficiaryIamUserId) {
        if (ticketId == null || candidateIamUserId == null || candidateRoles == null || candidateRoles.isEmpty()
            || excluded(candidateIamUserId, applicantIamUserId, beneficiaryIamUserId)) return false;
        if (!roles.findActiveIamUserIdsByRoleCodes(candidateRoles).contains(candidateIamUserId)) return false;
        return eligible(candidateIamUserId, candidateRoles, ticketContexts.resolveForScope(ticketId));
    }

    private boolean eligible(String candidateIamUserId, Set<String> candidateRoles, TicketObjectContext context) {
        try {
            var current = authorizations.resolve(candidateIamUserId, "APPROVAL_CANDIDATE");
            if (current.authorities().stream().noneMatch(candidateRoles::contains)) return false;
            return ticketScopes.resolve(current).allowsScoped(context);
        } catch (AccessDeniedException | IllegalArgumentException denied) {
            return false;
        }
    }

    private static boolean excluded(String candidate, String applicant, String beneficiary) {
        return candidate == null || candidate.equals(applicant) || candidate.equals(beneficiary);
    }
}
