package cn.servicehub.workflow.team;

import cn.servicehub.workflow.application.WorkflowConflictException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository @Profile("!mysql")
public class InMemorySupportQueueRepository implements SupportQueueRepository {
    private final Map<String,SupportQueue> queues=new ConcurrentHashMap<>();
    private final Map<String,WorkflowQueueRoutingSnapshot> snapshots=new ConcurrentHashMap<>();
    public List<SupportQueue> findAll(){return queues.values().stream().sorted(java.util.Comparator.comparing(SupportQueue::code)).toList();}
    public Optional<SupportQueue> findByCode(String code){return Optional.ofNullable(queues.get(code));}
    public synchronized SupportQueue save(SupportQueue v,long expected,String actor){SupportQueue old=queues.get(v.code());if((old==null?0:old.version())!=expected)throw new WorkflowConflictException();SupportQueue saved=new SupportQueue(v.code(),v.name(),v.owningOrganizationId(),v.serviceCatalogItemIds(),v.scopes(),v.members(),v.sharedClaimEnabled(),v.capacityLimit(),v.effectiveFrom(),v.effectiveUntil(),v.status(),expected+1);queues.put(saved.code(),saved);return saved;}
    public void saveRoutingSnapshot(WorkflowQueueRoutingSnapshot value){if(snapshots.putIfAbsent(value.id(),value)!=null)throw new WorkflowConflictException();}
    public List<WorkflowQueueRoutingSnapshot> findRoutingSnapshots(String ticketId){return snapshots.values().stream().filter(s->s.ticketId().equals(ticketId)).sorted(java.util.Comparator.comparing(WorkflowQueueRoutingSnapshot::capturedAt)).toList();}
}
