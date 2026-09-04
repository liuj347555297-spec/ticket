package cn.servicehub.workflow.routing;

import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.security.PlatformAuthorizationResolver;
import cn.servicehub.security.TicketAccessScopeResolver;
import cn.servicehub.ticket.application.TicketObjectContextResolver;
import cn.servicehub.workflow.team.SupportQueueEligibilityService;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Resolves a service-catalog node policy entirely on the server and captures its applied snapshot. */
@Service
public class NodeAssignmentResolver {
    public static final Set<String> DEFAULT_FIRST_LINE = Set.of("ROLE_FIRST_LINE_SUPPORT");
    private static final Set<String> SUPPORTED_NODES = Set.of("accept", "processing", "user_feedback", "closure");
    private final NodeAssignmentPolicyRepository policies;
    private final IamRoleProjectionRepository roles;
    private final IamUserProjectionRepository users;
    private final PlatformAuthorizationResolver authorizations;
    private final TicketAccessScopeResolver ticketScopes;
    private final TicketObjectContextResolver ticketContexts;
    private final SupportQueueEligibilityService queueEligibility;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock = Clock.systemUTC();
    public NodeAssignmentResolver(NodeAssignmentPolicyRepository policies, IamRoleProjectionRepository roles, IamUserProjectionRepository users,
                                  PlatformAuthorizationResolver authorizations, TicketAccessScopeResolver ticketScopes,
                                  TicketObjectContextResolver ticketContexts,SupportQueueEligibilityService queueEligibility) {
        this.policies = policies; this.roles = roles; this.users = users; this.authorizations = authorizations;
        this.ticketScopes = ticketScopes; this.ticketContexts = ticketContexts;this.queueEligibility=queueEligibility;
    }
    public Resolution resolveInitialAcceptance(String ticketId, String catalogItemId, String requesterIamUserId) {
        NodeAssignmentPolicy policy = policy(catalogItemId, "accept");
        List<String> candidates = eligible(ticketId, policy, requesterIamUserId);
        String selected = policy.mode() == NodeAssignmentMode.SYSTEM_RANDOM && !candidates.isEmpty() ? candidates.get(random.nextInt(candidates.size())) : null;
        policies.saveSnapshot(new NodeAssignmentSnapshot(ticketId, "accept", policy.mode(),policy.queueCode(), policy.candidateRoles(), policy.version(), selected, clock.instant()));
        return new Resolution(policy, selected, candidates.isEmpty());
    }
    /** Freezes the target for a transition into a handler node; requester input is never trusted without this check. */
    public Resolution resolveNextHandler(String ticketId, String catalogItemId, String targetNode, String requesterIamUserId, String requestedIamUserId) {
        NodeAssignmentPolicy policy = policy(catalogItemId, targetNode);
        List<String> candidates = eligible(ticketId, policy, requesterIamUserId);
        String selected = switch (policy.mode()) {
            case SYSTEM_RANDOM -> candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
            case PREVIOUS_HANDLER_SELECTS -> {
                if (requestedIamUserId == null || !candidates.contains(requestedIamUserId)) throw new IllegalArgumentException("Requested next handler is outside the active routing pool");
                yield requestedIamUserId;
            }
            case SHARED_QUEUE -> null;
        };
        policies.saveSnapshot(new NodeAssignmentSnapshot(ticketId, targetNode, policy.mode(),policy.queueCode(), policy.candidateRoles(), policy.version(), selected, clock.instant()));
        return new Resolution(policy, selected, candidates.isEmpty());
    }
    public boolean requiresPreviousHandlerSelection(String catalogItemId, String targetNode) { return policy(catalogItemId, targetNode).mode() == NodeAssignmentMode.PREVIOUS_HANDLER_SELECTS; }
    public List<HandlerCandidate> candidates(String ticketId, String catalogItemId, String targetNode, String requesterIamUserId) {
        NodeAssignmentPolicy policy = policy(catalogItemId, targetNode);
        return eligible(ticketId, policy, requesterIamUserId).stream()
            .map(id -> users.findActiveByIamUserId(id).map(user -> new HandlerCandidate(user.iamUserId(), user.displayName(), user.organization().name())).orElse(null))
            .filter(java.util.Objects::nonNull).toList();
    }
    public List<NodeAssignmentSnapshot> snapshots(String ticketId) { return policies.findSnapshots(ticketId); }
    public NodeAssignmentSnapshot latestSnapshot(String ticketId,String node){return policies.findSnapshots(ticketId).stream().filter(s->s.nodeKey().equals(node)).max(java.util.Comparator.comparing(NodeAssignmentSnapshot::capturedAt)).orElse(null);}
    public record HandlerCandidate(String iamUserId, String displayName, String organizationName) { }
    private NodeAssignmentPolicy policy(String catalogItemId, String node) {
        if (!SUPPORTED_NODES.contains(node)) throw new IllegalArgumentException("Workflow node is not assignable");
        return policies.findActive(catalogItemId, node).orElse(new NodeAssignmentPolicy(catalogItemId, node,
            NodeAssignmentMode.SYSTEM_RANDOM, DEFAULT_FIRST_LINE, 0, true));
    }
    private List<String> eligible(String ticketId, NodeAssignmentPolicy policy, String requesterIamUserId) {
        var ticketContext = ticketContexts.resolveForScope(ticketId);
        if(policy.mode()==NodeAssignmentMode.SHARED_QUEUE&&policy.queueCode()==null)return List.of();
        if(policy.queueCode()!=null)return queueEligibility.eligibleMembers(policy.queueCode(),policy.candidateRoles(),ticketContext,requesterIamUserId).stream().sorted().toList();
        return roles.findActiveIamUserIdsByRoleCodes(policy.candidateRoles()).stream()
            .filter(id -> !id.equals(requesterIamUserId))
            .filter(id -> users.findActiveByIamUserId(id).isPresent())
            .filter(id -> {
                try { return ticketScopes.resolve(authorizations.resolve(id, "ROUTING")).allowsScoped(ticketContext); }
                catch (RuntimeException denied) { return false; }
            }).sorted().toList();
    }
    public record Resolution(NodeAssignmentPolicy policy, String selectedIamUserId, boolean noEligibleCandidate) { }
}
