package cn.servicehub.workflow.web;

import cn.servicehub.ticket.domain.IdentitySnapshot;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import cn.servicehub.workflow.application.ApprovalTaskInbox;
import cn.servicehub.workflow.application.ApprovalTaskInboxItem;
import java.time.Instant;
import java.util.List;

/** Deliberately minimal approval worklist response; the full ticket remains behind per-object detail authorization. */
public record ApprovalTaskInboxResponse(List<Item> items, int page, int pageSize) {
    static ApprovalTaskInboxResponse from(ApprovalTaskInbox inbox) {
        return new ApprovalTaskInboxResponse(inbox.items().stream().map(Item::from).toList(), inbox.page(), inbox.pageSize());
    }

    public record Item(String taskType, String requestId, String approvalRequestId, String ticketId, String ticketTitle, TicketType ticketType, TicketStatus ticketStatus,
                TicketPriority ticketPriority, ServiceCatalogSummary serviceCatalogItem, IdentitySnapshot requester,
                String applicantIamUserId, String sourceNode, String targetNode, String actionCode, String summary,
                Instant requestedAt, Instant engineTaskCreatedAt, String decisionMode, int candidateApprovalCount,
                int requiredApprovalCount, boolean canDecide, String disabledReason) {
        static Item from(ApprovalTaskInboxItem item) {
            var ticket = item.ticket();
            return new Item(item.taskType(), item.requestId(), item.requestId(), item.ticketId(), ticket.title(), ticket.type(), ticket.status(), ticket.priority(),
                ticket.serviceCatalogItem(), ticket.requester(), item.applicantIamUserId(), item.sourceNode(), item.targetNode(), item.actionCode(), item.summary(),
                item.requestedAt(), item.engineTaskCreatedAt(), item.decisionMode(), item.candidateApprovalCount(), item.requiredApprovalCount(), item.canDecide(), item.disabledReason());
        }
    }
}
