package cn.servicehub.notification.application;

import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.notification.domain.MessageChannel;
import cn.servicehub.notification.domain.NotificationRouteRule;
import cn.servicehub.notification.domain.NotificationRouteRuleRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Resolves a route on the server for both actual delivery and safe back-office preview. */
@Service
public class NotificationRoutingService {
    private static final Set<String> ROUTING_READ_ROLES = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_SERVICE_MANAGER", "ROLE_AUDITOR");
    private final CurrentUserProvider currentUsers;
    private final IamUserProjectionRepository iamUsers;
    private final NotificationRouteRuleRepository routes;
    private final Map<MessageChannel, MessageChannelPort> adapters;

    public NotificationRoutingService(CurrentUserProvider currentUsers, IamUserProjectionRepository iamUsers,
                                      NotificationRouteRuleRepository routes, List<MessageChannelPort> adapters) {
        this.currentUsers = currentUsers;
        this.iamUsers = iamUsers;
        this.routes = routes;
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(MessageChannelPort::channel, item -> item));
    }

    public NotificationRoutingPreview previewForCurrentUser(String organizationIamOrganizationId, String event) {
        CurrentUser actor = currentUsers.requireCurrentUser();
        if (actor.authorities().stream().noneMatch(ROUTING_READ_ROLES::contains)) {
            throw new AccessDeniedException("Notification routing preview is not authorized");
        }
        String currentOrganization = iamUsers.findActiveByIamUserId(actor.iamUserId())
            .orElseThrow(() -> new AccessDeniedException("Active IAM projection is required"))
            .organization().iamOrganizationId();
        // A service manager/auditor may only inspect their own IAM organization. Platform admins
        // require an explicit data-scope authority for a different organization as well.
        if (!organizationIamOrganizationId.equals(currentOrganization)
            && !actor.authorities().contains("DATA_SCOPE_ORGANIZATION:" + organizationIamOrganizationId)) {
            throw new AccessDeniedException("Notification routing organization is not authorized");
        }
        NotificationRouteRule route = routes.findBest(organizationIamOrganizationId, event).orElse(null);
        if (route == null) return new NotificationRoutingPreview(organizationIamOrganizationId, event, "DEFAULT_IN_APP", null,
            MessageChannel.IN_APP, false, null);
        MessageChannel requested = route.preferredChannel();
        MessageChannelPort adapter = adapters.get(requested);
        boolean available = adapter != null && adapter.enabled();
        MessageChannel resolved = available ? requested : route.fallbackChannel();
        boolean fallback = requested != resolved;
        return new NotificationRoutingPreview(organizationIamOrganizationId, event, fallback ? "FALLBACK_APPLIED" : "MATCHED",
            requested, resolved, fallback, new NotificationRoutingPreview.MatchedRule(route.id(), 0, route.priority(), 300,
                route.includeDescendants(), route.enabled() ? "PUBLISHED" : "DISABLED"));
    }
}
