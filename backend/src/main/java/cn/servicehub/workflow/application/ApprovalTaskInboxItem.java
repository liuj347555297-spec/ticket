package cn.servicehub.workflow.application;

import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.workflow.domain.ControlledJumpRequest;
import java.time.Instant;

/** An inbox row joins a live Flowable task to its authorized ticket projection. */
public record ApprovalTaskInboxItem(ControlledJumpRequest request, Ticket ticket, Instant engineTaskCreatedAt,
                                    String decisionMode, int candidateApprovalCount, int requiredApprovalCount,
                                    boolean canDecide, String disabledReason) { }
