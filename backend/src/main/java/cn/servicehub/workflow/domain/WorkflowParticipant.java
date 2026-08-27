package cn.servicehub.workflow.domain;

import cn.servicehub.ticket.domain.IdentitySnapshot;
import java.time.Instant;

/** Immutable identity snapshot of an active workflow collaboration assignment. */
public record WorkflowParticipant(String ticketId, CollaborationRole role, IdentitySnapshot identity, Instant assignedAt) { }
