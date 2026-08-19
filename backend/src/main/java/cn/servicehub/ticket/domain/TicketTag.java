package cn.servicehub.ticket.domain;

/** A tag can be maintained centrally or supplied as a # prefixed free tag. */
public record TicketTag(String name, Kind kind) {
    public enum Kind { STANDARD, FREE }
}
