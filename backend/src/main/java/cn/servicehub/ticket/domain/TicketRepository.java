package cn.servicehub.ticket.domain;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public interface TicketRepository {
    CreateTicketResult createIdempotently(String actorIamUserId, String idempotencyKey, String requestFingerprint,
                                          Supplier<Ticket> ticketSupplier);

    Optional<Ticket> findById(String ticketId);

    List<Ticket> findAll(TicketQuery query);
}
