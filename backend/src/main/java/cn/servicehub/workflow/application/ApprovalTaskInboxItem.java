package cn.servicehub.workflow.application;

import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.Ticket;
import java.time.Instant;

/**
 * Browser-safe unified task projection. Candidate lists, counts and policy internals remain
 * server-side evidence: a row exists only after Flowable, identity and ticket authorization pass.
 */
public record ApprovalTaskInboxItem(String taskType, String requestId, String ticketId, Ticket ticket,
                                    String actionCode, String summary, Instant requestedAt,
                                    Instant engineTaskCreatedAt, String applicantIamUserId,
                                    String sourceNode, String targetNode, String decisionMode,
                                    int candidateApprovalCount, int requiredApprovalCount,
                                    boolean canDecide, String disabledReason) { }
