package cn.servicehub.ticketdraft;

import cn.servicehub.ticketdraft.TicketDraftModels.Draft;
import java.util.List;
import java.util.Optional;

public interface TicketDraftRepository {
    Optional<Draft> find(String id,String owner);
    List<Draft> list(String owner,int offset,int limit);
    long count(String owner);
    Draft save(Draft draft,long expectedVersion);
    boolean delete(String id,String owner,long version);
}
