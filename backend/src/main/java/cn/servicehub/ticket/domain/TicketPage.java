package cn.servicehub.ticket.domain;

import java.util.List;
import java.time.Instant;

public record TicketPage(List<Ticket> items, int page, int pageSize, long total,
                         String nextCursor, boolean hasMore, Instant snapshotAt) {
    public TicketPage {
        items = List.copyOf(items);
    }
    public TicketPage(List<Ticket> items, int page, int pageSize, long total) {
        this(items, page, pageSize, total, null, false, null);
    }
}
