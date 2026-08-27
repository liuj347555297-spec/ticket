package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.TicketRelation;
import cn.servicehub.ticket.domain.TicketRelationRepository;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryTicketRelationRepository implements TicketRelationRepository {
    private final ConcurrentHashMap<String, TicketRelation> relations = new ConcurrentHashMap<>();

    @Override public boolean createIfAbsent(TicketRelation relation) {
        return relations.putIfAbsent(key(relation), relation) == null;
    }

    @Override public List<TicketRelation> findByTicketId(String ticketId) {
        return relations.values().stream()
            .filter(relation -> relation.ticketId().equals(ticketId) || relation.relatedTicketId().equals(ticketId))
            .sorted(java.util.Comparator.comparing(TicketRelation::createdAt).reversed())
            .toList();
    }

    private String key(TicketRelation relation) {
        return relation.ticketId() + "\u001f" + relation.relatedTicketId() + "\u001f" + relation.relationType();
    }
}
