package cn.servicehub.workflow.domain;

import java.time.Instant;

/** Time-boxed, ticket-scoped authority delegation; it never changes the primary assignee. */
public record TicketDelegation(String id, String ticketId, String delegatorIamUserId, String delegateIamUserId,
                               Instant effectiveFrom, Instant effectiveUntil, Instant createdAt) { }
