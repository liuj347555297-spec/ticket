package cn.servicehub.workflow.team;

import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.PlatformAuthorizationResolver;
import cn.servicehub.security.TicketAccessScopeResolver;
import cn.servicehub.ticket.domain.TicketAccessScope;
import cn.servicehub.ticket.domain.TicketObjectContext;
import cn.servicehub.ticket.application.TicketNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Combines current identity, backoffice role, membership, personal scope and queue scope. */
@Service
public class SupportQueueEligibilityService {
    private final SupportQueueRepository queues; private final PlatformAuthorizationResolver authorizations;
    private final TicketAccessScopeResolver ticketScopes; private final Clock clock=Clock.systemUTC();
    public SupportQueueEligibilityService(SupportQueueRepository queues,PlatformAuthorizationResolver authorizations,TicketAccessScopeResolver ticketScopes){this.queues=queues;this.authorizations=authorizations;this.ticketScopes=ticketScopes;}
    public SupportQueue activeQueue(String code){SupportQueue q=queues.findByCode(code).orElseThrow(()->new AccessDeniedException("Support queue is unavailable"));if(!q.activeAt(clock.instant()))throw new AccessDeniedException("Support queue is unavailable");return q;}
    public boolean eligible(String code,String iamUserId,Set<String> requiredRoles,TicketObjectContext context){
        try{SupportQueue q=activeQueue(code);Instant now=clock.instant();if(q.members().stream().noneMatch(m->m.iamUserId().equals(iamUserId)&&m.activeAt(now)))return false;CurrentUser user=authorizations.resolve(iamUserId,"QUEUE");if(requiredRoles!=null&&!requiredRoles.isEmpty()&&user.authorities().stream().noneMatch(requiredRoles::contains))return false;return ticketScopes.resolve(user).allowsScoped(context)&&queueScope(q,iamUserId).allowsScoped(context);}catch(RuntimeException denied){return false;}
    }
    public void requireEligible(String code,CurrentUser user,Set<String> roles,TicketObjectContext context){if(!eligible(code,user.iamUserId(),roles,context))throw new AccessDeniedException("Support queue claim is not authorized");}
    public TicketAccessScope listingScope(String code,CurrentUser user){SupportQueue q;try{q=activeQueue(code);}catch(RuntimeException denied){throw new TicketNotFoundException(code);}Instant now=clock.instant();if(q.members().stream().noneMatch(m->m.iamUserId().equals(user.iamUserId())&&m.activeAt(now)))throw new TicketNotFoundException(code);TicketAccessScope personal=ticketScopes.resolve(user);if(!personal.scopedTicketRole()&&!personal.legacyDirectBypass())throw new TicketNotFoundException(code);return queueScope(q,user.iamUserId());}
    public Set<String> eligibleMembers(String code,Set<String> roles,TicketObjectContext context,String excluded){SupportQueue q=activeQueue(code);Instant now=clock.instant();Set<String> result=new LinkedHashSet<>();for(SupportQueueMember m:q.members())if(m.activeAt(now)&&!m.iamUserId().equals(excluded)&&eligible(code,m.iamUserId(),roles,context))result.add(m.iamUserId());return Set.copyOf(result);}
    public TicketAccessScope queueScope(SupportQueue q,String actor){Set<String> org=new LinkedHashSet<>(),cat=new LinkedHashSet<>(),system=new LinkedHashSet<>(),ci=new LinkedHashSet<>();for(SupportQueueScope s:q.scopes())switch(s.scopeType()){case ORGANIZATION->org.add(s.scopeId());case SERVICE_CATALOG->cat.add(s.scopeId());case SERVICE_SYSTEM->system.add(s.scopeId());case CONFIGURATION_ITEM->ci.add(s.scopeId());}return new TicketAccessScope(actor,Set.of("ROLE_QUEUE_SCOPE"),org,cat,system,ci,true,false,false);}
    public String scopeDigest(SupportQueue q){return digest(q.scopes().stream().map(s->s.scopeType()+":"+s.scopeId()).sorted().reduce("",(a,b)->a+b+"\n"));}
    public String contextDigest(TicketObjectContext c){return digest(c.requesterOrganizationId()+"\n"+c.serviceCatalogItemId()+"\n"+(c.serviceSystemCode()==null?"":c.serviceSystemCode())+"\n"+c.configurationItemIds().stream().sorted().reduce("",(a,b)->a+b+"\n"));}
    private String digest(String v){try{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
