package cn.servicehub.ticket.domain;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

public interface TicketRepository {
    CreateTicketResult createIdempotently(String actorIamUserId, String idempotencyKey, String requestFingerprint,
                                          Supplier<Ticket> ticketSupplier);

    Optional<Ticket> findById(String ticketId);

    List<Ticket> findAll(TicketQuery query);

    /**
     * Updates only server-derived lifecycle state with compare-and-set semantics. A false result
     * means that another action won first and the caller must reload rather than overwrite it.
     */
    boolean updateStatus(String ticketId, long expectedVersion, TicketStatus status, java.time.Instant updatedAt);

    /**
     * Allocates a monotonically increasing number for the given UTC business date. Implementations
     * must make this durable when a database profile is active; application-memory counters are not
     * safe across restarts.
     */
    long nextTicketSequence(LocalDate businessDate);
}
