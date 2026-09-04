package cn.servicehub.workflow.lifecycleapproval.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.domain.ServiceCatalogRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Publishing is deliberately separated from drafting so a browser cannot change live approval behavior by edit. */
@Service
public class LifecycleApprovalPolicyService {
    private static final Set<String> ADMIN = Set.of("ROLE_PLATFORM_ADMIN");
    private static final Set<String> ALLOWED_CANDIDATE_ROLES = Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private static final Set<WorkflowAction> GOVERNED_ACTIONS = Set.of(WorkflowAction.HOLD, WorkflowAction.ESCALATE, WorkflowAction.CANCEL, WorkflowAction.REOPEN, WorkflowAction.ASSIGN, WorkflowAction.ACCEPT, WorkflowAction.RESOLVE, WorkflowAction.CLOSE);
    private final LifecycleApprovalPolicyRepository policies; private final ServiceCatalogRepository catalog; private final CurrentUserProvider users; private final AuditEventPublisher audit; private final Clock clock=Clock.systemUTC();
    public LifecycleApprovalPolicyService(LifecycleApprovalPolicyRepository policies, ServiceCatalogRepository catalog, CurrentUserProvider users, AuditEventPublisher audit) { this.policies=policies; this.catalog=catalog; this.users=users; this.audit=audit; }
    public List<LifecycleApprovalPolicy> list() { requireAdmin(); return policies.findAll(); }
    @Transactional public LifecycleApprovalPolicy create(PolicyCommand c) { CurrentUser actor=requireAdmin(); Instant now=clock.instant(); LifecycleApprovalPolicy saved=policies.save(policy(null,c,"DRAFT",0,now,null),null); record(actor,"LIFECYCLE_APPROVAL_POLICY_DRAFT_CREATED",saved); return saved; }
    @Transactional public LifecycleApprovalPolicy updateDraft(String id, PolicyCommand c) { CurrentUser actor=requireAdmin(); LifecycleApprovalPolicy old=policies.findById(id).orElseThrow(()->new IllegalArgumentException("Lifecycle approval policy not found")); if (!"DRAFT".equals(old.status()) || c.expectedVersion()==null || c.expectedVersion()!=old.version()) throw new IllegalStateException("Only the current draft may be edited"); LifecycleApprovalPolicy saved=policies.save(policy(old,c,"DRAFT",old.version()+1,old.createdAt(),null),old.version()); record(actor,"LIFECYCLE_APPROVAL_POLICY_DRAFT_UPDATED",saved); return saved; }
    @Transactional public LifecycleApprovalPolicy publish(String id,long expectedVersion) { CurrentUser actor=requireAdmin(); LifecycleApprovalPolicy old=policies.findById(id).orElseThrow(()->new IllegalArgumentException("Lifecycle approval policy not found")); if (!"DRAFT".equals(old.status()) || old.version()!=expectedVersion) throw new IllegalStateException("Only the current draft may be published"); if (policies.findPublishedByAction(old.action().name()).stream().anyMatch(p -> !p.id().equals(old.id()) && sameScope(p, old))) throw new IllegalStateException("An equally scoped published policy already exists"); Instant now=clock.instant(); LifecycleApprovalPolicy saved=policies.save(new LifecycleApprovalPolicy(old.id(),old.name(),old.action(),old.serviceCatalogItemId(),old.priority(),old.candidateRoles(),old.decisionMode(),old.approvalThresholdPercent(),old.timeoutMinutes(),old.timeoutPolicyVersion(),old.escalationPolicyVersion(),"PUBLISHED",old.version()+1,old.createdAt(),now,now),old.version()); record(actor,"LIFECYCLE_APPROVAL_POLICY_PUBLISHED",saved); return saved; }
    @Transactional public LifecycleApprovalPolicy retire(String id,long expectedVersion) { CurrentUser actor=requireAdmin(); LifecycleApprovalPolicy old=policies.findById(id).orElseThrow(()->new IllegalArgumentException("Lifecycle approval policy not found")); if (!"PUBLISHED".equals(old.status()) || old.version()!=expectedVersion) throw new IllegalStateException("Only the current published policy may be retired"); Instant now=clock.instant(); LifecycleApprovalPolicy saved=policies.save(new LifecycleApprovalPolicy(old.id(),old.name(),old.action(),old.serviceCatalogItemId(),old.priority(),old.candidateRoles(),old.decisionMode(),old.approvalThresholdPercent(),old.timeoutMinutes(),old.timeoutPolicyVersion(),old.escalationPolicyVersion(),"RETIRED",old.version()+1,old.createdAt(),now,old.publishedAt()),old.version()); record(actor,"LIFECYCLE_APPROVAL_POLICY_RETIRED",saved); return saved; }
    private LifecycleApprovalPolicy policy(LifecycleApprovalPolicy old,PolicyCommand c,String status,long version,Instant created,Instant published) { Instant now=clock.instant(); String catalogId=blank(c.serviceCatalogItemId()); if (c.name()==null||c.name().isBlank()||c.name().trim().length()>120||c.action()==null||!GOVERNED_ACTIONS.contains(c.action())||c.candidateRoles()==null||c.candidateRoles().isEmpty()||!ALLOWED_CANDIDATE_ROLES.containsAll(c.candidateRoles())||!Set.of("ANY_ONE","ALL_OF","QUORUM").contains(c.decisionMode())||c.approvalThresholdPercent()<1||c.approvalThresholdPercent()>100||("ANY_ONE".equals(c.decisionMode()) && c.approvalThresholdPercent()!=100)||("ALL_OF".equals(c.decisionMode()) && c.approvalThresholdPercent()!=100)||c.timeoutMinutes()<1||c.timeoutMinutes()>43200||bad(c.timeoutPolicyVersion(),64)||bad(c.escalationPolicyVersion(),64)||(catalogId!=null && catalog.findById(catalogId).isEmpty())) throw new IllegalArgumentException("Lifecycle approval policy command is invalid"); return new LifecycleApprovalPolicy(old==null?UUID.randomUUID().toString():old.id(),c.name().trim(),c.action(),catalogId,c.priority(),Set.copyOf(c.candidateRoles()),c.decisionMode(),c.approvalThresholdPercent(),c.timeoutMinutes(),c.timeoutPolicyVersion().trim(),c.escalationPolicyVersion().trim(),status,version,created==null?now:created,now,published); }
    private CurrentUser requireAdmin(){ CurrentUser actor=users.requireCurrentUser(); if(actor.authorities().stream().noneMatch(ADMIN::contains)) throw new AccessDeniedException("Lifecycle approval policy administration is not authorized"); return actor; }
    private void record(CurrentUser actor,String action,LifecycleApprovalPolicy p){audit.publish(new AuditEvent(clock.instant(),"system",actor.iamUserId(),action,"lifecycleApprovalPolicy",p.id(),Map.of("version",Long.toString(p.version()),"status",p.status(),"actionCode",p.action().name())));}
    private boolean bad(String text,int max){return text==null||text.isBlank()||text.trim().length()>max;} private String blank(String text){return text==null||text.isBlank()?null:text.trim();}
    private boolean sameScope(LifecycleApprovalPolicy left, LifecycleApprovalPolicy right) { return java.util.Objects.equals(left.serviceCatalogItemId(), right.serviceCatalogItemId()) && left.priority()==right.priority(); }
    public record PolicyCommand(String name, WorkflowAction action, String serviceCatalogItemId, TicketPriority priority, Set<String> candidateRoles, String decisionMode, int approvalThresholdPercent, int timeoutMinutes, String timeoutPolicyVersion, String escalationPolicyVersion, Long expectedVersion) { }
}
