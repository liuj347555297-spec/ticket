package cn.servicehub.ticket.application;

public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String ignoredTicketId) {
        super("Ticket was not found");
    }
}
