package cn.servicehub.workflow.domain;

import cn.servicehub.ticket.domain.TicketStatus;
import java.time.Instant;

/** Platform-side projection of an engine instance; never accept any of its values from a browser. */
public record WorkflowInstance(String ticketId, String engineInstanceId, String currentNode, TicketStatus status,
                               TicketStatus resumeStatus, int escalationLevel, String primaryAssigneeIamUserId,
                               String processDefinitionId, Integer processDefinitionVersion,
                               long version, Instant createdAt, Instant updatedAt) {
}
