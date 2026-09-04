package cn.servicehub.workflow.routing;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.workflow.team.SupportQueueEligibilityService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Back-office governed routing policy maintenance; requesters cannot read or change candidate pools. */
@Service
public class WorkflowRoutingPolicyService {
    private static final Set<String> NODES = Set.of("accept", "processing", "user_feedback", "closure");
    private static final Set<String> ROLES = Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_SERVICE_MANAGER");
    private final NodeAssignmentPolicyRepository policies; private final CurrentUserProvider users; private final IamRoleProjectionRepository roles; private final AuditEventPublisher audit;private final SupportQueueEligibilityService queues; private final Clock clock = Clock.systemUTC();
    public WorkflowRoutingPolicyService(NodeAssignmentPolicyRepository policies, CurrentUserProvider users, IamRoleProjectionRepository roles, AuditEventPublisher audit,SupportQueueEligibilityService queues) { this.policies=policies; this.users=users; this.roles=roles; this.audit=audit;this.queues=queues; }
    public List<NodeAssignmentPolicy> list(String catalogItemId) { CurrentUser actor=manager(); List<NodeAssignmentPolicy> values=policies.findByCatalogItemId(catalogItemId); audit(actor,"WORKFLOW_ROUTING_POLICY_LISTED",catalogItemId,Map.of("returned",String.valueOf(values.size()))); return values; }
    public NodeAssignmentPolicy save(String catalogItemId, String node, NodeAssignmentMode mode,String queueCode, Set<String> candidateRoles, boolean enabled, long version) {
        CurrentUser actor=manager(); if(!NODES.contains(node)||mode==null||candidateRoles==null||candidateRoles.isEmpty()||!ROLES.containsAll(candidateRoles) || ("accept".equals(node) && mode == NodeAssignmentMode.PREVIOUS_HANDLER_SELECTS)) throw new IllegalArgumentException("Workflow routing policy is invalid");
        if (roles.findActiveIamUserIdsByRoleCodes(candidateRoles).isEmpty()) throw new IllegalArgumentException("No active IAM handler matches the routing roles");
        if(mode==NodeAssignmentMode.SHARED_QUEUE){if(queueCode==null)throw new IllegalArgumentException("Shared queue policy requires queue code");if(!queues.activeQueue(queueCode).sharedClaimEnabled())throw new IllegalArgumentException("Queue does not allow shared claim");}else if(queueCode!=null)queues.activeQueue(queueCode);
        NodeAssignmentPolicy saved=policies.save(new NodeAssignmentPolicy(catalogItemId,node,mode,queueCode,candidateRoles,version,enabled),version,actor.iamUserId()); audit(actor,"WORKFLOW_ROUTING_POLICY_SAVED",catalogItemId+":"+node,Map.of("mode",mode.name(),"queueCode",queueCode==null?"NONE":queueCode,"version",String.valueOf(saved.version()),"enabled",String.valueOf(enabled))); return saved;
    }
    public NodeAssignmentPolicy save(String c,String n,NodeAssignmentMode m,Set<String>r,boolean e,long v){return save(c,n,m,null,r,e,v);}
    private CurrentUser manager(){CurrentUser actor=users.requireCurrentUser();if(!(actor.authorities().contains("ROLE_SERVICE_MANAGER")||actor.authorities().contains("ROLE_PLATFORM_ADMIN")))throw new AccessDeniedException("Workflow routing policy management is not authorized");return actor;}
    private void audit(CurrentUser actor,String action,String id,Map<String,String> attributes){audit.publish(new AuditEvent(clock.instant(),MDC.get("requestId")==null?"system":MDC.get("requestId"),actor.iamUserId(),action,"workflow-routing-policy",id,attributes));}
}
