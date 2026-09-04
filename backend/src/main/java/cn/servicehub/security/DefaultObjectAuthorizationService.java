package cn.servicehub.security;

import cn.servicehub.ticket.application.TicketObjectContextResolver;
import cn.servicehub.ticket.domain.TicketObjectContext;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Ticket roles authorize an action class; server-resolved object facts and the current scope
 * package authorize the concrete ticket. No broad role alone grants group-wide ticket access.
 */
@Component
public class DefaultObjectAuthorizationService implements ObjectAuthorizationService {
    private static final Set<String> TICKET_READ_ROLES = Set.of(
        "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER",
        "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR");
    /** Auditors may inspect ticket history but must not retrieve sensitive raw attachments. */
    private static final Set<String> ATTACHMENT_DOWNLOAD_ALL_ROLES = Set.of(
        "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private static final Set<String> TICKET_MUTATION_ROLES = Set.of(
        "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private final TicketAccessScopeResolver scopes;
    private final TicketObjectContextResolver contexts;
    public DefaultObjectAuthorizationService(TicketAccessScopeResolver scopes, TicketObjectContextResolver contexts) {
        this.scopes = scopes; this.contexts = contexts;
    }

    @Override
    public void requireAuthorized(CurrentUser user, ObjectAuthorizationRequest request) {
        if ("knowledge-document".equals(request.resourceType())) {
            // Reading is additionally constrained by KnowledgeService to PUBLISHED content.  Do not
            // give this broad gate the responsibility for deciding publication state.
            if (request.action() == ObjectAction.READ && hasAnyAuthority(user, Set.of(
                "ROLE_REQUESTER", "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT",
                "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR"))) return;
            if (hasAnyAuthority(user, Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN"))) return;
            throw new AccessDeniedException("Knowledge administration is not authorized");
        }
        if (!"ticket".equals(request.resourceType())) {
            throw new AccessDeniedException("Unsupported protected resource");
        }
        if (request.action() == ObjectAction.CREATE) {
            if (hasAnyAuthority(user, Set.of("ROLE_REQUESTER", "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT",
                "ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN"))) {
                return;
            }
            throw new AccessDeniedException("Ticket creation is not authorized");
        }
        TicketObjectContext context = contexts.resolve(request.resourceId());
        var scope = scopes.resolve(user);
        boolean requester = user.iamUserId().equals(context.requesterIamUserId());
        boolean scoped = scope.allowsScoped(context);
        if (request.action() == ObjectAction.READ && (requester
            || (hasAnyAuthority(user, TICKET_READ_ROLES) && scoped))) {
            return;
        }
        if (request.action() == ObjectAction.DOWNLOAD_ATTACHMENT && (requester
            || (hasAnyAuthority(user, ATTACHMENT_DOWNLOAD_ALL_ROLES) && scoped))) {
            return;
        }
        if (request.action() == ObjectAction.COMMENT && (requester || (hasAnyAuthority(user, TICKET_MUTATION_ROLES)
            && scoped))) {
            return;
        }
        if ((request.action() == ObjectAction.UPDATE || request.action() == ObjectAction.UPLOAD_ATTACHMENT || request.action() == ObjectAction.ASSIGN
            || request.action() == ObjectAction.TRANSFER || request.action() == ObjectAction.APPROVE)
            && ((hasAnyAuthority(user, TICKET_MUTATION_ROLES) && scoped)
                || ((request.action() == ObjectAction.UPDATE || request.action() == ObjectAction.UPLOAD_ATTACHMENT) && requester))) {
            return;
        }
        throw new AccessDeniedException("Ticket action is not authorized");
    }

    private boolean hasAnyAuthority(CurrentUser user, Set<String> expected) {
        return user.authorities().stream().anyMatch(expected::contains);
    }
}
