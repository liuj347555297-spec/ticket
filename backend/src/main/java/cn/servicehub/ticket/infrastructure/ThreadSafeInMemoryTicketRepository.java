package cn.servicehub.ticket.infrastructure;

import cn.servicehub.ticket.domain.CreateTicketResult;
import cn.servicehub.ticket.domain.IdempotencyConflictException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketQuery;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketPageSlice;
import cn.servicehub.ticket.domain.TicketObjectContext;
import cn.servicehub.servicesystem.domain.ServiceSystemRepository;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowTaskStatus;
import cn.servicehub.notification.domain.NotificationRepository;
import cn.servicehub.sla.domain.TicketSlaTargetRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

/** Development-only transactional substitute. Replace with a unique database constraint before production. */
@Repository
@Profile("!mysql")
public class ThreadSafeInMemoryTicketRepository implements TicketRepository {
    private final ConcurrentHashMap<String, Ticket> ticketsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IdempotencyRecordKey, IdempotencyRecord> idempotencyRecords = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<java.time.LocalDate, AtomicLong> ticketSequences = new ConcurrentHashMap<>();
    private final TicketWorkflowRepository workflows; private final NotificationRepository notifications;
    private final TicketSlaTargetRepository slaTargets; private final ServiceSystemRepository systems;

    public ThreadSafeInMemoryTicketRepository(TicketWorkflowRepository workflows, NotificationRepository notifications,
                                              TicketSlaTargetRepository slaTargets, ServiceSystemRepository systems) {
        this.workflows = workflows; this.notifications = notifications; this.slaTargets = slaTargets; this.systems = systems;
    }

    @Override
    public CreateTicketResult createIdempotently(String actorIamUserId, String idempotencyKey, String requestFingerprint,
                                                  Supplier<Ticket> ticketSupplier) {
        IdempotencyRecordKey key = new IdempotencyRecordKey(actorIamUserId, idempotencyKey);
        AtomicBoolean created = new AtomicBoolean(false);
        IdempotencyRecord record = idempotencyRecords.compute(key, (ignored, existing) -> {
            if (existing != null) {
                if (!existing.requestFingerprint().equals(requestFingerprint)) {
                    throw new IdempotencyConflictException();
                }
                return existing;
            }
            Ticket ticket = ticketSupplier.get();
            ticketsById.put(ticket.id(), ticket);
            created.set(true);
            return new IdempotencyRecord(requestFingerprint, ticket);
        });
        return new CreateTicketResult(record.ticket(), !created.get());
    }

    @Override
    public Optional<Ticket> findById(String ticketId) {
        return Optional.ofNullable(ticketsById.get(ticketId));
    }

    @Override
    @Deprecated(forRemoval = true)
    public List<Ticket> findAll(TicketQuery query) {
        return ticketsById.values().stream()
            .filter(ticket -> query.status() == null || ticket.status() == query.status())
            .filter(ticket -> query.type() == null || ticket.type() == query.type())
            .filter(ticket -> ticket.matchesKeyword(query.keyword()))
            .sorted(order())
            .toList();
    }

    @Override
    public TicketPageSlice findPage(TicketQuery query) {
        if (query.accessScope() == null || query.snapshotAt() == null || query.pageSize() < 1 || query.pageSize() > 100) {
            throw new IllegalArgumentException("Bounded ticket query is required");
        }
        java.util.Set<String> queueIds = queueIds(query);
        List<Ticket> authorized = ticketsById.values().stream()
            .filter(ticket -> !ticket.createdAt().isAfter(query.snapshotAt()))
            .filter(ticket -> query.status() == null || ticket.status() == query.status())
            .filter(ticket -> query.type() == null || ticket.type() == query.type())
            .filter(ticket -> query.priority() == null || ticket.priority() == query.priority())
            .filter(ticket -> matches(query.serviceCatalogItemId(), ticket.serviceCatalogItem().id(), ticket.serviceCatalogItem().name()))
            .filter(ticket -> matches(query.requesterOrganizationId(), ticket.requester().organizationId(), ticket.requester().organizationName()))
            .filter(ticket -> query.createdFrom() == null || !ticket.createdAt().isBefore(query.createdFrom()))
            .filter(ticket -> query.createdTo() == null || ticket.createdAt().isBefore(query.createdTo()))
            .filter(ticket -> ticket.matchesKeyword(query.keyword()))
            .filter(ticket -> authorized(query, ticket))
            .filter(ticket -> inQueue(query, ticket, queueIds))
            .sorted(order()).toList();
        long total = authorized.size();
        List<Ticket> after = authorized.stream().filter(ticket -> afterCursor(query, ticket)).limit(query.pageSize() + 1L).toList();
        boolean hasMore = after.size() > query.pageSize();
        return new TicketPageSlice(hasMore ? after.subList(0, query.pageSize()) : after, hasMore, total);
    }

    private boolean authorized(TicketQuery query, Ticket ticket) {
        var scope = query.accessScope();
        String system = systems.findTicketSnapshot(ticket.id()).map(value -> value.systemCode()).orElse(null);
        return ticket.requester().iamUserId().equals(scope.actorIamUserId())
            || scope.allowsScoped(new TicketObjectContext(ticket.requester().iamUserId(), ticket.requester().organizationId(),
                ticket.serviceCatalogItem().id(), system, java.util.Set.copyOf(ticket.relatedConfigurationItemIds()), false));
    }

    private java.util.Set<String> queueIds(TicketQuery query) {
        String actor = query.accessScope().actorIamUserId();
        return switch (query.queue()) {
            case MY_TODO -> java.util.Set.copyOf(workflows.findTodoTicketIds(actor, query.accessScope().roleCodes()));
            case MY_DONE, TODAY_COMPLETED -> java.util.Set.copyOf(workflows.findCompletedTicketIds(actor));
            case TO_READ -> java.util.Set.copyOf(notifications.findUnreadTicketIds(actor));
            case OVERDUE -> java.util.Set.copyOf(slaTargets.findBreachedTicketIds());
            default -> java.util.Set.of();
        };
    }

    private boolean inQueue(TicketQuery query, Ticket ticket, java.util.Set<String> queueIds) {
        String actor = query.accessScope().actorIamUserId();
        boolean personal=switch (query.queue()) {
            case ALL -> true;
            case MY_REQUESTED -> ticket.requester().iamUserId().equals(actor);
            case DRAFTS -> ticket.requester().iamUserId().equals(actor) && ticket.status() == cn.servicehub.ticket.domain.TicketStatus.DRAFT;
            case MY_TODO, MY_DONE, TO_READ, OVERDUE -> queueIds.contains(ticket.id());
            case TODAY_COMPLETED -> queueIds.contains(ticket.id())
                && (ticket.status() == cn.servicehub.ticket.domain.TicketStatus.RESOLVED || ticket.status() == cn.servicehub.ticket.domain.TicketStatus.CLOSED)
                && java.time.LocalDate.ofInstant(ticket.updatedAt(), java.time.ZoneOffset.UTC).equals(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        };
        if(!personal)return false;if(query.teamQueueCode()==null)return true;if(query.teamQueueScope()==null)return false;boolean task=workflows.findTasks(ticket.id()).stream().anyMatch(t->query.teamQueueCode().equals(t.queueCode())&&(t.status()==WorkflowTaskStatus.OPEN||t.status()==WorkflowTaskStatus.CLAIMED));if(!task)return false;String system=systems.findTicketSnapshot(ticket.id()).map(v->v.systemCode()).orElse(null);return query.teamQueueScope().allowsScoped(new TicketObjectContext(ticket.requester().iamUserId(),ticket.requester().organizationId(),ticket.serviceCatalogItem().id(),system,java.util.Set.copyOf(ticket.relatedConfigurationItemIds()),false));
    }

    private boolean afterCursor(TicketQuery query, Ticket ticket) {
        if (query.afterCreatedAt() == null) return true;
        int time = ticket.createdAt().compareTo(query.afterCreatedAt());
        return time < 0 || (time == 0 && ticket.id().compareTo(query.afterId()) < 0);
    }
    private static boolean matches(String filter, String id, String name) {
        if (filter == null || filter.isBlank()) return true;
        String value = filter.trim().toLowerCase(java.util.Locale.ROOT);
        return (id != null && id.toLowerCase(java.util.Locale.ROOT).contains(value))
            || (name != null && name.toLowerCase(java.util.Locale.ROOT).contains(value));
    }
    private static Comparator<Ticket> order() { return Comparator.comparing(Ticket::createdAt).reversed().thenComparing(Ticket::id, Comparator.reverseOrder()); }

    @Override
    public boolean updateStatus(String ticketId, long expectedVersion, cn.servicehub.ticket.domain.TicketStatus status,
                                java.time.Instant updatedAt) {
        AtomicBoolean updated = new AtomicBoolean(false);
        ticketsById.computeIfPresent(ticketId, (ignored, current) -> {
            if (current.version() != expectedVersion) {
                return current;
            }
            updated.set(true);
            return new Ticket(current.id(), current.type(), status, current.priority(), current.title(), current.description(), current.descriptionFormat(), current.descriptionHtml(),
                current.structuredFields(), current.tags(), current.relatedConfigurationItemIds(), current.requester(),
                current.serviceCatalogItem(), current.serviceCatalogFormVersion(), current.createdAt(), updatedAt, current.version() + 1);
        });
        return updated.get();
    }

    @Override
    public boolean updateDescription(String ticketId, long expectedVersion, String description, cn.servicehub.ticket.domain.TicketDescriptionFormat format, String descriptionHtml, java.time.Instant updatedAt) {
        AtomicBoolean updated = new AtomicBoolean(false);
        ticketsById.computeIfPresent(ticketId, (ignored, current) -> {
            if (current.version() != expectedVersion) return current;
            updated.set(true);
            return new Ticket(current.id(), current.type(), current.status(), current.priority(), current.title(), description, format, descriptionHtml,
                current.structuredFields(), current.tags(), current.relatedConfigurationItemIds(), current.requester(), current.serviceCatalogItem(), current.serviceCatalogFormVersion(), current.createdAt(), updatedAt, current.version() + 1);
        });
        return updated.get();
    }

    @Override
    public long nextTicketSequence(java.time.LocalDate businessDate) {
        return ticketSequences.computeIfAbsent(businessDate, ignored -> new AtomicLong()).incrementAndGet();
    }

    private record IdempotencyRecordKey(String actorIamUserId, String idempotencyKey) {
    }

    private record IdempotencyRecord(String requestFingerprint, Ticket ticket) {
    }
}
