package cn.servicehub.ticketdraft;

import cn.servicehub.ticketdraft.TicketDraftModels.*;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.audit.*;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.workflow.application.WorkflowConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.owasp.html.Sanitizers;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TicketDraftService {
    private final TicketDraftRepository drafts; private final CurrentUserProvider users;private final AuditEventPublisher audit;
    public TicketDraftService(TicketDraftRepository drafts,CurrentUserProvider users,AuditEventPublisher audit){this.drafts=drafts;this.users=users;this.audit=audit;}
    public Page list(int page,int size){String owner=users.requireCurrentUser().iamUserId();return new Page(drafts.list(owner,(page-1)*size,size).stream().map(Summary::of).toList(),page,size,drafts.count(owner));}
    public Draft get(String id){return drafts.find(id,users.requireCurrentUser().iamUserId()).orElseThrow(()->new TicketNotFoundException(id));}
    @Transactional public Draft save(String id,Input input,long expected){
        var actor=users.requireCurrentUser();if(input.version()!=expected||expected<0)throw new WorkflowConflictException();
        JsonNode payload=validate(input.payload());var old=drafts.find(id,actor.iamUserId()).orElse(null);
        // The same PUT can safely reconcile a response lost immediately after commit.
        if(old!=null&&old.version()==expected+1&&old.payload().equals(payload))return old;
        if(old==null?expected!=0:old.version()!=expected)throw new WorkflowConflictException();
        Instant now=Instant.now();String title=payload.path("form").path("title").asText().strip();
        Draft result=drafts.save(new Draft(id,actor.iamUserId(),title.isEmpty()?"未命名工单":title,payload,expected+1,old==null?now:old.createdAt(),now),expected);
        audit.publish(new AuditEvent(now,"draft",actor.iamUserId(),"TICKET_DRAFT_SAVED","ticket-draft",id,Map.of("version",String.valueOf(result.version()))));return result;
    }
    @Transactional public void delete(String id,long version){var actor=users.requireCurrentUser();Draft d=get(id);if(d.version()!=version||!drafts.delete(id,actor.iamUserId(),version))throw new WorkflowConflictException();audit.publish(new AuditEvent(Instant.now(),"draft",actor.iamUserId(),"TICKET_DRAFT_DELETED","ticket-draft",id,Map.of()));}
    private JsonNode validate(JsonNode node){
        if(node==null||!node.isObject()||node.toString().getBytes(StandardCharsets.UTF_8).length>131072)throw new IllegalArgumentException();
        node=node.deepCopy();Set<String> allowed=Set.of("form","fieldValues","formVersion");node.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw new IllegalArgumentException();});
        var form=node.path("form");if(!form.isObject()||!node.path("fieldValues").isObject()||node.path("fieldValues").size()>100)throw new IllegalArgumentException();
        Set<String> keys=Set.of("systemCode","moduleCode","catalogId","type","title","descriptionHtml","descriptionText","tags");form.fieldNames().forEachRemaining(k->{if(!keys.contains(k))throw new IllegalArgumentException();});
        for(String key:keys){if(key.equals("tags"))continue;if(!form.path(key).isTextual())throw new IllegalArgumentException();}
        if(form.path("title").asText().length()>200||form.path("descriptionHtml").asText().length()>50000||form.path("descriptionText").asText().length()>20000)throw new IllegalArgumentException();
        for(String key:List.of("systemCode","moduleCode","catalogId"))if(!form.path(key).asText().matches("^[A-Za-z0-9_-]{0,64}$"))throw new IllegalArgumentException();
        if(!Set.of("INCIDENT","SERVICE_REQUEST","ACCESS_REQUEST","PROBLEM","CHANGE").contains(form.path("type").asText()))throw new IllegalArgumentException();
        if(!form.path("tags").isArray()||form.path("tags").size()>20)throw new IllegalArgumentException();for(var tag:form.path("tags"))if(!tag.isTextual()||tag.asText().length()>100)throw new IllegalArgumentException();
        node.path("fieldValues").fields().forEachRemaining(e->{if(!e.getKey().matches("^[a-z][a-z0-9_]{0,63}$")||!simpleValue(e.getValue()))throw new IllegalArgumentException();});
        if(!node.path("formVersion").isNull()&&(!node.path("formVersion").canConvertToInt()||node.path("formVersion").asInt()<1))throw new IllegalArgumentException();
        ((com.fasterxml.jackson.databind.node.ObjectNode)form).put("descriptionHtml",Sanitizers.FORMATTING.and(Sanitizers.BLOCKS).and(Sanitizers.LINKS).sanitize(form.path("descriptionHtml").asText()));return node;
    }
    private boolean simpleValue(JsonNode v){if(v.isBoolean()||v.isNumber())return true;if(v.isTextual())return v.asText().length()<=10000;if(v.isArray()&&v.size()<=100){for(var item:v)if(!item.isTextual()||item.asText().length()>500)return false;return true;}return false;}
}
