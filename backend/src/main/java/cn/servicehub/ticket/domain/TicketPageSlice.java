package cn.servicehub.ticket.domain;

import java.util.List;

/** Repository-level bounded page plus an authorization-filtered compatibility count. */
public record TicketPageSlice(List<Ticket> items, boolean hasMore, long total) {
    public TicketPageSlice { items = List.copyOf(items); }
}
