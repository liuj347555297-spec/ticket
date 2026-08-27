package cn.servicehub.ticket.domain;

/**
 * Controlled ticket-to-ticket relationship types.  They are deliberately separate from the
 * lifecycle state: linking a problem or change must not silently advance either work item.
 */
public enum TicketRelationType {
    RELATED,
    DUPLICATE_OF,
    PARENT_OF,
    PROBLEM_REFERENCE,
    CHANGE_REFERENCE
}
