package cn.servicehub.notification.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.notification.domain.Notification;
import cn.servicehub.notification.domain.NotificationRepository;
import cn.servicehub.notification.domain.NotificationRouteRule;
import cn.servicehub.notification.domain.NotificationRouteRuleRepository;
import cn.servicehub.notification.domain.NotificationDelivery;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.ticket.domain.Ticket;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Only domain services call this component. The HTTP API exposes no recipient parameter: targets
 * are derived from the ticket requester snapshot or from a server-validated workflow assignment.
 */
@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final IamUserProjectionRepository iamUsers;
    private final CurrentUserProvider currentUserProvider;
    private final AuditEventPublisher audit;
    private final NotificationRouteRuleRepository routes;
    private final Clock clock = Clock.systemUTC();

    public NotificationService(NotificationRepository repository, IamUserProjectionRepository iamUsers,
                               CurrentUserProvider currentUserProvider, AuditEventPublisher audit, NotificationRouteRuleRepository routes) {
        this.repository = repository; this.iamUsers = iamUsers; this.currentUserProvider = currentUserProvider; this.audit = audit; this.routes = routes;
    }

    @Transactional
    public void ticketCreated(Ticket ticket) {
        createForActiveUser(ticket.requester().iamUserId(), "TICKET_CREATED", "工单已提交", "您的工单已提交，等待服务台受理。", ticket,
            Map.of("ticketStatus", ticket.status().name()));
    }

    @Transactional
    public void workflowAction(Ticket ticket, String action, String actorIamUserId, String assignmentTargetIamUserId) {
        // The requested target is validated by TicketWorkflowService against IAM before it reaches here.
        if (assignmentTargetIamUserId != null && !assignmentTargetIamUserId.equals(actorIamUserId)) {
            createForActiveUser(assignmentTargetIamUserId, "TICKET_ASSIGNED", "您有新的待处理工单", "工单已分派给您，请及时处理。", ticket,
                Map.of("action", action));
        }
        if (!ticket.requester().iamUserId().equals(actorIamUserId)) {
            createForActiveUser(ticket.requester().iamUserId(), "TICKET_PROGRESS", "工单处理状态更新", "您的工单已有新的处理进展。", ticket,
                Map.of("action", action, "ticketStatus", ticket.status().name()));
        }
    }

    public NotificationPage myNotifications(int page, int pageSize, String readState, String category) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        requireActive(user.iamUserId());
        return new NotificationPage(repository.findByRecipient(user.iamUserId(), readState, category, (page - 1) * pageSize, pageSize), page, pageSize, repository.countByRecipient(user.iamUserId(), readState, category));
    }

    @Transactional
    public Notification markRead(String notificationId, long version) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        requireActive(user.iamUserId());
        Notification updated = repository.markRead(notificationId, user.iamUserId(), version, clock.instant()).orElseThrow(() -> new AccessDeniedException("Notification is unavailable"));
            // Do not disclose existence of another user's notification.
        audit.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "NOTIFICATION_READ", "notification", notificationId, Map.of()));
        return updated;
    }

    public NotificationDeliveryPage myDeliveries(String notificationId, int page, int pageSize) {
        CurrentUser user = currentUserProvider.requireCurrentUser(); requireActive(user.iamUserId());
        List<NotificationDelivery> items = repository.findDeliveries(notificationId, user.iamUserId(), (page - 1) * pageSize, pageSize).orElseThrow(() -> new AccessDeniedException("Notification is unavailable"));
        return new NotificationDeliveryPage(items, page, pageSize, repository.countDeliveries(notificationId, user.iamUserId()));
    }
    public long unreadCount() {
        CurrentUser user = currentUserProvider.requireCurrentUser(); requireActive(user.iamUserId());
        return repository.countByRecipient(user.iamUserId(), "UNREAD", null);
    }

    private void createForActiveUser(String recipientIamUserId, String category, String title, String body, Ticket ticket, Map<String, String> payload) {
        var recipient = iamUsers.findActiveByIamUserId(recipientIamUserId); if (recipient.isEmpty()) return;
        Instant now = clock.instant();
        Map<String, String> completePayload = new java.util.HashMap<>(payload);
        completePayload.put("ticketId", ticket.id());
        String normalizedCategory = category.startsWith("WORKFLOW") ? "WORKFLOW" : "TICKET";
        NotificationRouteRule route = routes == null ? null : routes.findBest(recipient.get().organization().iamOrganizationId(), category).orElse(null);
        if (route != null) { completePayload.put("routeRuleId", route.id()); completePayload.put("preferredChannel", route.preferredChannel().name()); completePayload.put("providerChannelCode", route.providerChannelCode() == null ? "" : route.providerChannelCode()); }
        completePayload.put("targetPath", "/tickets/" + ticket.id());
        // Five-minute server-owned aggregation bucket: browser retries and bursty repeated events
        // create one inbox item per recipient/ticket/event, while a later meaningful event remains visible.
        String deduplicationKey = recipientIamUserId + "|" + ticket.id() + "|" + category + "|" + (now.getEpochSecond() / 300);
        repository.save(new Notification("NTF-" + UUID.randomUUID(), recipientIamUserId, normalizedCategory, title, body, ticket.id(), completePayload, deduplicationKey, null, now, 0));
    }
    private void requireActive(String iamUserId) { if (iamUsers.findActiveByIamUserId(iamUserId).isEmpty()) throw new AccessDeniedException("Active IAM projection is required"); }
    private String requestId() { String id = MDC.get("requestId"); return id == null ? "system" : id; }
}
