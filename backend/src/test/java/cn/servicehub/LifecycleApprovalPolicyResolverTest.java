package cn.servicehub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import cn.servicehub.workflow.application.ApprovalCandidateScopeResolver;
import cn.servicehub.workflow.application.LifecycleApprovalPolicyResolver;
import cn.servicehub.workflow.application.WorkflowStateException;
import cn.servicehub.workflow.domain.WorkflowAction;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LifecycleApprovalPolicyResolverTest {
    @Test void freezesMostSpecificPublishedQuorumPolicyAndExcludesApplicantAndTarget() {
        LifecycleApprovalPolicyRepository policies=Mockito.mock(LifecycleApprovalPolicyRepository.class); ApprovalCandidateScopeResolver candidates=Mockito.mock(ApprovalCandidateScopeResolver.class);
        Instant now=Instant.parse("2026-08-31T00:00:00Z");
        LifecycleApprovalPolicy wildcard=policy("wildcard",null,null,"ANY_ONE",100); LifecycleApprovalPolicy specific=policy("specific","catalog-1",TicketPriority.P1,"QUORUM",67);
        when(policies.findPublishedByAction("ASSIGN")).thenReturn(List.of(wildcard,specific));
        when(candidates.resolve(ticket().id(),specific.candidateRoles(),"applicant","target")).thenReturn(Set.of("a","b","c"));
        var resolved=new LifecycleApprovalPolicyResolver(policies,candidates).resolve(ticket(),WorkflowAction.ASSIGN,"applicant","target",now);
        assertEquals("specific",resolved.policy().id()); assertEquals(Set.of("a","b","c"),resolved.candidateIamUserIds()); assertEquals(3,resolved.requiredApprovalCount()); assertEquals(now.plusSeconds(3600),resolved.dueAt());
    }
    @Test void refusesToStartWhenPolicyWouldHaveNoIndependentApprover() {
        LifecycleApprovalPolicyRepository policies=Mockito.mock(LifecycleApprovalPolicyRepository.class); ApprovalCandidateScopeResolver candidates=Mockito.mock(ApprovalCandidateScopeResolver.class);
        LifecycleApprovalPolicy policy=policy("one",null,null,"ANY_ONE",100); when(policies.findPublishedByAction("ASSIGN")).thenReturn(List.of(policy)); when(candidates.resolve(ticket().id(),policy.candidateRoles(),"applicant",null)).thenReturn(Set.of());
        assertThrows(WorkflowStateException.class,()->new LifecycleApprovalPolicyResolver(policies,candidates).resolve(ticket(),WorkflowAction.ASSIGN,"applicant",null,Instant.now()));
    }
    private static LifecycleApprovalPolicy policy(String id,String catalog,TicketPriority priority,String mode,int threshold){Instant now=Instant.parse("2026-01-01T00:00:00Z");return new LifecycleApprovalPolicy(id,id,WorkflowAction.ASSIGN,catalog,priority,Set.of("ROLE_SERVICE_MANAGER"),mode,threshold,60,"TIMEOUT-V1","AUDIT-V1","PUBLISHED",1,now,now,now);}
    private static Ticket ticket(){Instant now=Instant.parse("2026-01-01T00:00:00Z");return new Ticket("TKT-20260101-000001",TicketType.INCIDENT,TicketStatus.PENDING_ASSIGNMENT,TicketPriority.P1,"title","description",null,null,Map.of(),List.of(),List.of(),new IdentitySnapshot("requester","requester","org","org",null,now),new ServiceCatalogSummary("catalog-1","catalog"),1,now,now,0);}
}
