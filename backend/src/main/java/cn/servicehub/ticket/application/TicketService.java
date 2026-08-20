package cn.servicehub.ticket.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.application.ServiceCatalogService;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.notification.application.NotificationService;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.ServiceCatalogSummary;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketPage;
import cn.servicehub.ticket.domain.TicketPriority;
import cn.servicehub.ticket.domain.TicketQuery;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import cn.servicehub.workflow.application.TicketWorkflowService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectAuthorizationService authorizationService;
    private final IdentitySnapshotResolver identitySnapshotResolver;
    private final AuditEventPublisher auditEventPublisher;
    private final ServiceCatalogService serviceCatalogService;
    private final TicketWorkflowService workflowService;
    private final NotificationService notificationService;
    private final Clock clock = Clock.systemUTC();

    public TicketService(TicketRepository ticketRepository, CurrentUserProvider currentUserProvider,
                         ObjectAuthorizationService authorizationService, IdentitySnapshotResolver identitySnapshotResolver,
                         AuditEventPublisher auditEventPublisher, ServiceCatalogService serviceCatalogService,
                         TicketWorkflowService workflowService, NotificationService notificationService) {
        this.ticketRepository = ticketRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.identitySnapshotResolver = identitySnapshotResolver;
        this.auditEventPublisher = auditEventPublisher;
        this.serviceCatalogService = serviceCatalogService;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
    }

    public CreateTicketResult create(TicketCreateCommand command, String idempotencyKey) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        authorizationService.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", "NEW", ObjectAction.CREATE,
            Map.of("serviceCatalogItemId", command.serviceCatalogItemId())));
        ServiceCatalogItem catalogItem = serviceCatalogService.validateTicketInput(command);
        CreateTicketResult result = ticketRepository.createIdempotently(user.iamUserId(), idempotencyKey, command.fingerprint(), () -> {
            var now = clock.instant();
            Ticket ticket = new Ticket(nextTicketId(now), command.type(), TicketStatus.SUBMITTED, TicketPriority.P3,
                command.title(), command.description(), command.structuredFields(), command.tags(), command.relatedConfigurationItemIds(),
                identitySnapshotResolver.snapshotFor(user), new ServiceCatalogSummary(catalogItem.id(), catalogItem.name()), now, now, 0);
            return ticket;
        });
        if (!result.replayed()) {
            Ticket ticket = result.ticket();
            workflowService.startTicket(ticket, user);
            notificationService.ticketCreated(ticket);
            auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_CREATED", "ticket", ticket.id(),
                Map.of("type", ticket.type().name(), "catalogItemId", ticket.serviceCatalogItem().id())));
        }
        return result;
    }

    public Ticket get(String ticketId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireRead(user, ticket);
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_READ", "ticket", ticket.id(), Map.of()));
        return ticket;
    }

    public TicketPage list(int page, int pageSize, TicketStatus status, TicketType type, String keyword) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        List<Ticket> readable = ticketRepository.findAll(new TicketQuery(status, type, keyword)).stream()
            .filter(ticket -> canRead(user, ticket)).toList();
        int from = Math.min((page - 1) * pageSize, readable.size());
        int to = Math.min(from + pageSize, readable.size());
        List<Ticket> items = readable.subList(from, to);
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_LISTED", "ticket", "collection",
            Map.of("returned", String.valueOf(items.size()))));
        return new TicketPage(items, page, pageSize, readable.size());
    }

    private boolean canRead(CurrentUser user, Ticket ticket) {
        try {
            requireRead(user, ticket);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException ignored) {
            return false;
        }
    }

    private void requireRead(CurrentUser user, Ticket ticket) {
        authorizationService.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.READ,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
    }

    private String nextTicketId(java.time.Instant now) {
        LocalDate businessDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        long number = ticketRepository.nextTicketSequence(businessDate);
        if (number > 999_999) {
            throw new IllegalStateException("Daily ticket sequence is exhausted");
        }
        return "TKT-" + businessDate.toString().replace("-", "") + "-" + String.format("%06d", number);
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "system" : requestId;
    }
}
