package cn.servicehub.ticket.domain;

/** Storage format is explicit so only server-sanitized rich text may be rendered as HTML. */
public enum TicketDescriptionFormat {
    PLAIN_TEXT,
    RICH_TEXT
}
