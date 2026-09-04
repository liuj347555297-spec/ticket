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
import cn.servicehub.security.TicketAccessScopeResolver;
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
import cn.servicehub.workflow.application.TicketWorkflowService;
import cn.servicehub.servicesystem.application.ServiceSystemRegistryService;
import cn.servicehub.workflow.team.SupportQueueEligibilityService;
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
    private final TicketDescriptionSanitizer descriptionSanitizer;
    private final ServiceSystemRegistryService serviceSystems;
    private final TicketAccessScopeResolver ticketScopes;
    private final TicketCursorCodec cursors;
    private final SupportQueueEligibilityService supportQueues;
    private final Clock clock = Clock.systemUTC();

    public TicketService(TicketRepository ticketRepository, CurrentUserProvider currentUserProvider,
                         ObjectAuthorizationService authorizationService, IdentitySnapshotResolver identitySnapshotResolver,
                         AuditEventPublisher auditEventPublisher, ServiceCatalogService serviceCatalogService,
                         TicketWorkflowService workflowService, NotificationService notificationService, SlaService slaService,
                         IntegrationService integrationService, TicketDescriptionSanitizer descriptionSanitizer,
                         ServiceSystemRegistryService serviceSystems, TicketAccessScopeResolver ticketScopes,
                         TicketCursorCodec cursors,SupportQueueEligibilityService supportQueues) {
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
        this.descriptionSanitizer = descriptionSanitizer;
        this.serviceSystems = serviceSystems;
        this.ticketScopes = ticketScopes;
        this.cursors = cursors;
        this.supportQueues=supportQueues;
    }

    @Transactional
    public CreateTicketResult create(TicketCreateCommand command, String idempotencyKey) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        authorizationService.requireAuthorized(user, new ObjectAuthorizationRequest("ticket", "NEW", ObjectAction.CREATE,
            Map.of("serviceCatalogItemId", command.serviceCatalogItemId())));
        ServiceCatalogItem catalogItem = serviceCatalogService.validateTicketInput(command);
        ServiceSystemRegistryService.ResolvedTicketSelection serviceSystemSelection = serviceSystems.validateForTicket(
            command.serviceSystemCode(), command.serviceSystemModuleCode(), catalogItem.id());
        Map<String, Object> structuredFields = serviceCatalogService.normalizeStructuredFields(command);
        List<String> configurationItemIds = serviceCatalogService.configurationItemReferences(command, structuredFields);
        cn.servicehub.ticket.domain.IdentitySnapshot requesterSnapshot = identitySnapshotResolver.snapshotFor(user);
        integrationService.validateConfigurationItemIds(configurationItemIds, requesterSnapshot.organizationId());
        CreateTicketResult result = ticketRepository.createIdempotently(user.iamUserId(), idempotencyKey, command.fingerprint(), () -> {
            var now = clock.instant();
            Ticket ticket = new Ticket(nextTicketId(now), command.type(), TicketStatus.SUBMITTED, TicketPriority.P3,
                command.title(), command.description(), command.descriptionFormat(), command.descriptionHtml(), structuredFields, command.tags(), configurationItemIds,
                requesterSnapshot, new ServiceCatalogSummary(catalogItem.id(), catalogItem.name()), command.serviceCatalogFormVersion(), now, now, 0);
            return ticket;
        });
        if (!result.replayed()) {
            Ticket ticket = result.ticket();
            serviceSystems.captureTicketSnapshot(ticket.id(), serviceSystemSelection);
            integrationService.associateTicketConfigurationItems(ticket);
            Ticket routed = workflowService.startTicket(ticket, user);
            slaService.onTicketCreated(routed);
            notificationService.ticketCreated(routed);
            auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_CREATED", "ticket", ticket.id(),
                Map.of("type", ticket.type().name(), "catalogItemId", ticket.serviceCatalogItem().id(), "catalogFormVersion", String.valueOf(ticket.serviceCatalogFormVersion()))));
            return new CreateTicketResult(routed, false);
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
        return list(page, pageSize, status, type, null, null, null, null, null, keyword, queue,null, null);
    }

    public TicketPage list(int requestedPage, int pageSize, TicketStatus status, TicketType type, TicketPriority priority,
                           String serviceCatalogItemId, String requesterOrganizationId,
                           java.time.LocalDate createdFrom, java.time.LocalDate createdTo,
                           String keyword, TicketQueue queue,String teamQueueCode, String cursor) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        if(queue!=null&&teamQueueCode!=null)throw new IllegalArgumentException("Personal and team queues are mutually exclusive");
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) throw new IllegalArgumentException("Ticket creation range is invalid");
        java.time.Instant createdFromInclusive = createdFrom == null ? null : createdFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        java.time.Instant createdToExclusive = createdTo == null ? null : createdTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        TicketQueue effectiveQueue = queue == null ? TicketQueue.ALL : queue;
        String normalizedCatalog = serviceCatalogItemId == null ? null : serviceCatalogItemId.trim();
        String normalizedOrganization = requesterOrganizationId == null ? null : requesterOrganizationId.trim();
        String normalizedKeyword = keyword == null ? null : keyword.trim().replaceAll("[\\t\\r\\n ]+", " ");
        String filterDigest = cursors.filterDigest(status, type, priority, normalizedCatalog, normalizedOrganization,
            createdFromInclusive, createdToExclusive, effectiveQueue,teamQueueCode, normalizedKeyword, pageSize);
        int page; java.time.Instant snapshotAt; java.time.Instant afterCreatedAt = null; String afterId = null;
        if (cursor == null || cursor.isBlank()) {
            if (requestedPage != 1) throw new IllegalArgumentException("A cursor is required after the first ticket page");
            page = 1; snapshotAt = clock.instant();
        } else {
            var decoded = cursors.decode(cursor, user, filterDigest, pageSize);
            page = decoded.page(); snapshotAt = decoded.snapshotAt();
            afterCreatedAt = decoded.lastCreatedAt(); afterId = decoded.lastId();
        }
        var personalScope=ticketScopes.resolve(user);var teamScope=teamQueueCode==null?null:supportQueues.listingScope(teamQueueCode,user);
        var query = new TicketQuery(status, type, priority, normalizedCatalog, normalizedOrganization, createdFromInclusive, createdToExclusive,
            normalizedKeyword, effectiveQueue,teamQueueCode, personalScope,teamScope,
            snapshotAt, afterCreatedAt, afterId, pageSize);
        var slice = ticketRepository.findPage(query);
        String nextCursor = null;
        if (slice.hasMore() && !slice.items().isEmpty()) {
            Ticket last = slice.items().getLast();
            nextCursor = cursors.encode(user, filterDigest, pageSize, page, snapshotAt, last.createdAt(), last.id());
        }
        auditEventPublisher.publish(new AuditEvent(clock.instant(), requestId(), user.iamUserId(), "TICKET_LISTED", "ticket", "collection",
            Map.of("returned", String.valueOf(slice.items().size()), "queue", effectiveQueue.name(), "page", String.valueOf(page))));
        return new TicketPage(slice.items(), page, pageSize, slice.total(), nextCursor, slice.hasMore(), snapshotAt);
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
