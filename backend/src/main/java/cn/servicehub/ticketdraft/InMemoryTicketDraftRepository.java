package cn.servicehub.ticketdraft;

import cn.servicehub.ticketdraft.TicketDraftModels.Draft;
import cn.servicehub.workflow.application.WorkflowConflictException;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository @Profile("!mysql")
public class InMemoryTicketDraftRepository implements TicketDraftRepository {
    private final Map<String,Draft> drafts = new ConcurrentHashMap<>();
    public Optional<Draft> find(String id,String owner){return Optional.ofNullable(drafts.get(id)).filter(d->d.ownerId().equals(owner));}
    public List<Draft> list(String owner,int offset,int limit){return drafts.values().stream().filter(d->d.ownerId().equals(owner)).sorted(Comparator.comparing(Draft::updatedAt).reversed().thenComparing(Draft::id)).skip(offset).limit(limit).toList();}
    public long count(String owner){return drafts.values().stream().filter(d->d.ownerId().equals(owner)).count();}
    public synchronized Draft save(Draft d,long expected){var old=drafts.get(d.id());if(old==null?expected!=0:!old.ownerId().equals(d.ownerId())||old.version()!=expected)throw new WorkflowConflictException();drafts.put(d.id(),d);return d;}
    public synchronized boolean delete(String id,String owner,long version){var d=drafts.get(id);return d!=null&&d.ownerId().equals(owner)&&d.version()==version&&drafts.remove(id,d);}
}
