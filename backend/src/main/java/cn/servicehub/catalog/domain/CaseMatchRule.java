package cn.servicehub.catalog.domain;

import cn.servicehub.ticket.domain.TicketTag;

/** A non-empty AND group. Criteria that are null are intentionally ignored. */
public record CaseMatchRule(long id, String caseId, boolean enabled, String catalogItemId, String configurationItemId,
                            String fieldCode, String fieldValue, String tagName, TicketTag.Kind tagKind,
                            String errorCode, String keyword, int score) {
}
