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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

/** Development-only transactional substitute. Replace with a unique database constraint before production. */
@Repository
@Profile("!mysql")
public class ThreadSafeInMemoryTicketRepository implements TicketRepository {
    private final ConcurrentHashMap<String, Ticket> ticketsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IdempotencyRecordKey, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<java.time.LocalDate, AtomicLong> ticketSequences = new ConcurrentHashMap<>();

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

    @Override
    public boolean updateStatus(String ticketId, long expectedVersion, cn.servicehub.ticket.domain.TicketStatus status,
                                java.time.Instant updatedAt) {
        AtomicBoolean updated = new AtomicBoolean(false);
        ticketsById.computeIfPresent(ticketId, (ignored, current) -> {
            if (current.version() != expectedVersion) {
                return current;
            }
            updated.set(true);
            return new Ticket(current.id(), current.type(), status, current.priority(), current.title(), current.description(),
                current.structuredFields(), current.tags(), current.relatedConfigurationItemIds(), current.requester(),
                current.serviceCatalogItem(), current.createdAt(), updatedAt, current.version() + 1);
        });
        return updated.get();
    }

    @Override
    public long nextTicketSequence(java.time.LocalDate businessDate) {
        return ticketSequences.computeIfAbsent(businessDate, ignored -> new AtomicLong()).incrementAndGet();
    }

    private record IdempotencyRecordKey(String actorIamUserId, String idempotencyKey) {
    }

    private record IdempotencyRecord(String requestFingerprint, Ticket ticket) {
    }
}
