package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketQuery;
import cn.servicehub.ticket.domain.TicketRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;

/** Development-only transactional substitute. Replace with a unique database constraint before production. */
@Repository
public class ThreadSafeInMemoryTicketRepository implements TicketRepository {
    private final ConcurrentHashMap<String, Ticket> ticketsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IdempotencyRecordKey, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();

    @Override
    public CreateTicketResult createIdempotently(String actorIamUserId, String idempotencyKey, String requestFingerprint,
                                                  Supplier<Ticket> ticketSupplier) {
        IdempotencyRecordKey key = new IdempotencyRecordKey(actorIamUserId, idempotencyKey);
        AtomicBoolean created = new AtomicBoolean(false);
        IdempotencyRecord record = idempotencyRecords.compute(key, (ignored, existing) -> {
            if (existing != null) {
                if (!existing.requestFingerprint().equals(requestFingerprint)) {
                    throw new IdempotencyConflictException();
                }
                return existing;
            }
            Ticket ticket = ticketSupplier.get();
            ticketsById.put(ticket.id(), ticket);
            created.set(true);
            return new IdempotencyRecord(requestFingerprint, ticket);
        });
        return new CreateTicketResult(record.ticket(), !created.get());
    }

    @Override
    public Optional<Ticket> findById(String ticketId) {
        return Optional.ofNullable(ticketsById.get(ticketId));
    }

    @Override
    public List<Ticket> findAll(TicketQuery query) {
        return ticketsById.values().stream()
            .filter(ticket -> query.status() == null || ticket.status() == query.status())
            .filter(ticket -> query.type() == null || ticket.type() == query.type())
            .filter(ticket -> ticket.matchesKeyword(query.keyword()))
            .sorted(Comparator.comparing(Ticket::createdAt).reversed().thenComparing(Ticket::id))
            .toList();
    }

    private record IdempotencyRecordKey(String actorIamUserId, String idempotencyKey) {
    }

    private record IdempotencyRecord(String requestFingerprint, Ticket ticket) {
    }
}
