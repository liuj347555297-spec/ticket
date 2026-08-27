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

    public record Item(String approvalRequestId, String ticketId, String ticketTitle, TicketType ticketType, TicketStatus ticketStatus,
                TicketPriority ticketPriority, ServiceCatalogSummary serviceCatalogItem, IdentitySnapshot requester,
                String applicantIamUserId, String sourceNode, String targetNode, String reason, Instant requestedAt,
                Instant engineTaskCreatedAt, String decisionMode, int candidateApprovalCount, int requiredApprovalCount,
                boolean canDecide, String disabledReason) {
        static Item from(ApprovalTaskInboxItem item) {
            var request = item.request();
            var ticket = item.ticket();
            return new Item(request.id(), ticket.id(), ticket.title(), ticket.type(), ticket.status(), ticket.priority(),
                ticket.serviceCatalogItem(), ticket.requester(), request.applicantIamUserId(), request.sourceNode(), request.targetNode(),
                request.reason(), request.createdAt(), item.engineTaskCreatedAt(), item.decisionMode(), item.candidateApprovalCount(),
                item.requiredApprovalCount(), item.canDecide(), item.disabledReason());
        }
    }
}
