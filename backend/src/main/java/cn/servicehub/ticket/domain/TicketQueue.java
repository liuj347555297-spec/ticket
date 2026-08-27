package cn.servicehub.ticket.domain;

/** Fixed personal work queues. Querying is server-controlled; callers cannot supply another person's IAM ID. */
public enum TicketQueue {
    ALL,
    MY_TODO,
    OVERDUE,
    TODAY_COMPLETED,
    MY_DONE,
    MY_REQUESTED,
    DRAFTS,
    TO_READ
}
