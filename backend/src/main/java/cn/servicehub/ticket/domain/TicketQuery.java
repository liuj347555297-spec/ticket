package cn.servicehub.ticket.domain;

public record TicketQuery(TicketStatus status, TicketType type, String keyword) {
}
