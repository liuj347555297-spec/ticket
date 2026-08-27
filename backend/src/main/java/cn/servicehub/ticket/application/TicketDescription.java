package cn.servicehub.ticket.application;

import cn.servicehub.ticket.domain.TicketDescriptionFormat;

/** Sanitized rich markup and the derived plain-text projection are stored separately. */
public record TicketDescription(TicketDescriptionFormat format, String plainText, String sanitizedHtml) {
}
