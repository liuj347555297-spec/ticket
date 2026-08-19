package cn.servicehub.ticket.domain;

import java.util.List;

public record TicketPage(List<Ticket> items, int page, int pageSize, long total) {
    public TicketPage {
        items = List.copyOf(items);
    }
}
