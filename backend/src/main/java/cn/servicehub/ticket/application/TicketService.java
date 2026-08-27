package cn.servicehub.ticket.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.catalog.application.ServiceCatalogService;
import cn.servicehub.catalog.domain.ServiceCatalogItem;
import cn.servicehub.notification.application.NotificationService;
import cn.servicehub.integration.application.IntegrationService;
import cn.servicehub.sla.application.SlaService;
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
import cn.servicehub.ticket.domain.TicketQueue;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.ticket.domain.TicketType;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import cn.servicehub.workflow.application.TicketWorkflowService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SlaService slaService;
    private final IntegrationService integrationService;
    private final TicketWorkflowRepository workflowRepository;
    private final TicketSlaTargetRepository slaTargets;
    private final cn.servicehub.notification.domain.NotificationRepository notificationRepository;
    private final TicketDescriptionSanitizer descriptionSanitizer;
    private final Clock clock = Clock.systemUTC();

    public TicketService(TicketRepository ticketRepository, CurrentUserProvider currentUserProvider,
                         ObjectAuthorizationService authorizationService, IdentitySnapshotResolver identitySnapshotResolver,
                         AuditEventPublisher auditEventPublisher, ServiceCatalogService serviceCatalogService,
                         TicketWorkflowService workflowService, NotificationService notificationService, SlaService slaService,
                         IntegrationService integrationService, TicketWorkflowRepository workflowRepository,
                         TicketSlaTargetRepository slaTargets, cn.servicehub.notification.domain.NotificationRepository notificationRepository,
                         TicketDescriptionSanitizer descriptionSanitizer) {
        this.ticketRepository = ticketRepository;
        this.currentUserProvider = currentUserProvider;
        this.authorizationService = authorizationService;
        this.identitySnapshotResolver = identitySnapshotResolver;
        this.auditEventPublisher = auditEventPublisher;
        this.serviceCatalogService = serviceCatalogService;
        this.workflowService = workflowService;
        this.notificationService = notificationService;
        this.slaService = slaService;
        this.integrationService = integrationService;
        this.workflowRepository = workflowRepository;
        this.slaTargets = slaTargets;
        this.notificationRepository = notificationRepository;
        this.descriptionSanitizer = descriptionSanitizer;
    }

    @Transactional
    public CreateTicketResult create(TicketCreateCommand command, String idempotencyKey) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        authorizationService.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", "NEW", ObjectAction.CREATE,
            Map.of("serviceCatalogItemId", command.serviceCatalogItemId())));
        ServiceCatalogItem catalogItem = serviceCatalogService.validateTicketInput(command);
        integrationService.validateConfigurationItemIds(command.relatedConfigurationItemIds());
        CreateTicketResult result = ticketRepository.createIdempotently(user.iamUserId(), idempotencyKey, command.fingerprint(), () -> {
            var now = clock.instant();
            Ticket ticket = new Ticket(nextTicketId(now), command.type(), TicketStatus.SUBMITTED, TicketPriority.P3,
                command.title(), command.description(), command.descriptionFormat(), command.descriptionHtml(), command.structuredFields(), command.tags(), command.relatedConfigurationItemIds(),
                identitySnapshotResolver.snapshotFor(user), new ServiceCatalogSummary(catalogItem.id(), catalogItem.name()), now, now, 0);
            return ticket;
        });
        if (!result.replayed()) {
            Ticket ticket = result.ticket();
            integrationService.associateTicketConfigurationItems(ticket);
            workflowService.startTicket(ticket, user);
            slaService.onTicketCreated(ticket);
            notificationService.ticketCreated(ticket);
            auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_CREATED", "ticket", ticket.id(),
                Map.of("type", ticket.type().name(), "catalogItemId", ticket.serviceCatalogItem().id())));
        }
        return result;
    }

    @Transactional
    public Ticket updateDescription(String ticketId, long expectedVersion, String rawDescription, cn.servicehub.ticket.domain.TicketDescriptionFormat format) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        authorizationService.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", ticket.id(), ObjectAction.UPDATE,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
        if (ticket.status() != TicketStatus.SUBMITTED) throw new IllegalStateException("Ticket description can only be updated immediately after submission");
        TicketDescription description = descriptionSanitizer.sanitize(rawDescription, format, ticketId);
        if (!ticketRepository.updateDescription(ticketId, expectedVersion, description.plainText(), description.format(), description.sanitizedHtml(), clock.instant())) {
            throw new cn.servicehub.workflow.application.WorkflowConflictException();
        }
        Ticket updated = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_DESCRIPTION_UPDATED", "ticket", ticketId,
            Map.of("format", description.format().name(), "inlineImageCount", String.valueOf(description.sanitizedHtml() == null ? 0 : description.sanitizedHtml().split("<img ", -1).length - 1))));
        return updated;
    }

    public Ticket get(String ticketId) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        Ticket ticket = ticketRepository.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId));
        requireRead(user, ticket);
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_READ", "ticket", ticket.id(), Map.of()));
        return ticket;
    }

    public TicketPage list(int page, int pageSize, TicketStatus status, TicketType type, String keyword, TicketQueue queue) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        TicketQueue effectiveQueue = queue == null ? TicketQueue.ALL : queue;
        java.util.Set<String> queueTicketIds = queueTicketIds(user, effectiveQueue);
        List<Ticket> readable = ticketRepository.findAll(new TicketQuery(status, type, keyword)).stream()
            .filter(ticket -> canRead(user, ticket))
            .filter(ticket -> belongsToQueue(ticket, user, effectiveQueue, queueTicketIds)).toList();
        int from = Math.min((page - 1) * pageSize, readable.size());
        int to = Math.min(from + pageSize, readable.size());
        List<Ticket> items = readable.subList(from, to);
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_LISTED", "ticket", "collection",
            Map.of("returned", String.valueOf(items.size()), "queue", effectiveQueue.name())));
        return new TicketPage(items, page, pageSize, readable.size());
    }

    private java.util.Set<String> queueTicketIds(CurrentUser user, TicketQueue queue) {
        return switch (queue) {
            case MY_TODO -> java.util.Set.copyOf(workflowRepository.findTodoTicketIds(user.iamUserId(), user.authorities()));
            case MY_DONE, TODAY_COMPLETED -> java.util.Set.copyOf(workflowRepository.findCompletedTicketIds(user.iamUserId()));
            case TO_READ -> java.util.Set.copyOf(notificationRepository.findUnreadTicketIds(user.iamUserId()));
            case OVERDUE -> java.util.Set.copyOf(slaTargets.findBreachedTicketIds());
            default -> java.util.Set.of();
        };
    }

    private boolean belongsToQueue(Ticket ticket, CurrentUser user, TicketQueue queue, java.util.Set<String> queueTicketIds) {
        return switch (queue) {
            case ALL -> true;
            case MY_REQUESTED -> user.iamUserId().equals(ticket.requester().iamUserId());
            case DRAFTS -> user.iamUserId().equals(ticket.requester().iamUserId()) && ticket.status() == TicketStatus.DRAFT;
            case MY_TODO, MY_DONE, TO_READ -> queueTicketIds.contains(ticket.id());
            case OVERDUE -> queueTicketIds.contains(ticket.id());
            case TODAY_COMPLETED -> queueTicketIds.contains(ticket.id())
                && (ticket.status() == TicketStatus.RESOLVED || ticket.status() == TicketStatus.CLOSED)
                && java.time.LocalDate.ofInstant(ticket.updatedAt(), ZoneOffset.UTC).equals(java.time.LocalDate.now(ZoneOffset.UTC));
        };
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
