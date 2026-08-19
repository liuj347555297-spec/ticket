package cn.servicehub.security;

import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Conservative interim policy used until IAM data scopes and service queues are integrated.
 * Every ticket call still passes a server-resolved object context through this gate.
 */
@Component
public class DefaultObjectAuthorizationService implements ObjectAuthorizationService {
    private static final Set<String> TICKET_READ_ALL_ROLES = Set.of(
        "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER",
        "ROLE_PLATFORM_ADMIN", "ROLE_AUDITOR");

    @Override
    public void requireAuthorized(CurrentUser user, ObjectAuthorizationRequest request) {
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
        if (request.action() == ObjectAction.READ && (hasAnyAuthority(user, TICKET_READ_ALL_ROLES)
            || user.iamUserId().equals(request.serverResolvedContext().get("requesterIamUserId")))) {
            return;
        }
        throw new AccessDeniedException("Ticket action is not authorized");
    }

    private boolean hasAnyAuthority(CurrentUser user, Set<String> expected) {
        return user.authorities().stream().anyMatch(expected::contains);
    }
}
