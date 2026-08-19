package cn.servicehub.ticket.domain;

public record CreateTicketResult(Ticket ticket, boolean replayed) {
}
