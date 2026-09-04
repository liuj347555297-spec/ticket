package cn.servicehub.ticket.domain;

import java.time.Instant;

public record TicketQuery(TicketStatus status, TicketType type, TicketPriority priority, String serviceCatalogItemId,
                          String requesterOrganizationId, Instant createdFrom, Instant createdTo,
                          String keyword, TicketQueue queue,String teamQueueCode,
                          TicketAccessScope accessScope,TicketAccessScope teamQueueScope, Instant snapshotAt,
                          Instant afterCreatedAt, String afterId, int pageSize) {
    public TicketQuery(TicketStatus status, TicketType type, String keyword) {
        this(status, type, null, null, null, null, null, keyword, TicketQueue.ALL,null, null,null, Instant.MAX, null, null, Integer.MAX_VALUE);
    }
}
