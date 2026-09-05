package cn.servicehub.workflow.processing;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRepository;
import cn.servicehub.ticket.domain.TicketStatus;
import cn.servicehub.workflow.application.WorkflowStateException;
import cn.servicehub.workflow.domain.TicketWorkflowRepository;
import cn.servicehub.workflow.domain.WorkflowInstance;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessingDetailsService {
    private static final Set<String> EVENT_SOURCES = Set.of("PHONE", "EMAIL", "MONITORING_ALERT", "ON_SITE_FEEDBACK", "OTHER");
    private static final Set<String> CAUSE_CATEGORIES = Set.of("HARDWARE", "SOFTWARE_DEFECT", "CONFIGURATION", "NETWORK", "ACCESS_CONTROL", "DATA", "USER_OPERATION", "EXTERNAL_DEPENDENCY", "UNDER_INVESTIGATION");
    private final ProcessingDetailsRepository details;
    private final TicketRepository tickets;
    private final TicketWorkflowRepository workflows;
    private final CurrentUserProvider users;
    private final ObjectAuthorizationService authorization;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();

    public ProcessingDetailsService(ProcessingDetailsRepository details, TicketRepository tickets,
                                    TicketWorkflowRepository workflows, CurrentUserProvider users,
                                    ObjectAuthorizationService authorization, AuditEventPublisher audit) {
        this.details = details; this.tickets = tickets; this.workflows = workflows; this.users = users;
        this.authorization = authorization; this.audit = audit;
    }

    public ProcessingDetailsResponse get(String ticketId) {
        CurrentUser actor = users.requireCurrentUser();
        Ticket ticket = ticket(ticketId);
        require(actor, ticket, ObjectAction.READ);
        boolean editable = canEdit(actor, ticket);
        return details.findByTicketId(ticketId).map(value -> ProcessingDetailsResponse.from(value, editable))
            .orElseGet(() -> ProcessingDetailsResponse.empty(ticketId, editable));
    }

    @Transactional
    public ProcessingDetailsResponse save(String ticketId, long expectedVersion, ProcessingDetailsCommand command) {
        CurrentUser actor = users.requireCurrentUser();
        Ticket ticket = ticket(ticketId);
        require(actor, ticket, ObjectAction.UPDATE);
        WorkflowInstance lockedWorkflow = workflows.findInstanceForUpdate(ticket.id()).orElseThrow(WorkflowStateException::new);
        if (!canEdit(actor, ticket, lockedWorkflow)) throw new AccessDeniedException("Only the current primary handler may edit processing details");
        Instant now = clock.instant();
        ProcessingDetails saved = details.save(new ProcessingDetails(ticketId,
            code(command.eventSource(), EVENT_SOURCES), text(command.proposingOrganization(), 160, false),
            command.onSiteSupportRequired(), code(command.causeCategory(), CAUSE_CATEGORIES),
            text(command.processingDescription(), 4000, true), text(command.resolutionDescription(), 4000, true),
            command.thirdPartyHandled(), text(command.currentProgress(), 1000, true), expectedVersion + 1,
            actor.iamUserId(), now), expectedVersion);
        audit.publish(new AuditEvent(now, requestId(), actor.iamUserId(), "TICKET_PROCESSING_DETAILS_SAVED", "ticket", ticketId,
            Map.of("version", Long.toString(saved.version()))));
        return ProcessingDetailsResponse.from(saved, true);
    }

    private boolean canEdit(CurrentUser actor, Ticket ticket) {
        if (ticket.status() == TicketStatus.CLOSED || ticket.status() == TicketStatus.CANCELLED) return false;
        return workflows.findInstance(ticket.id()).map(instance -> canEdit(actor, ticket, instance)).orElse(false);
    }
    private boolean canEdit(CurrentUser actor, Ticket ticket, WorkflowInstance instance) {
        return ticket.status() != TicketStatus.CLOSED && ticket.status() != TicketStatus.CANCELLED
            && instance.status() != TicketStatus.CLOSED && instance.status() != TicketStatus.CANCELLED
            && actor.iamUserId().equals(instance.primaryAssigneeIamUserId());
    }

    private Ticket ticket(String ticketId) { return tickets.findById(ticketId).orElseThrow(() -> new TicketNotFoundException(ticketId)); }
    private void require(CurrentUser actor, Ticket ticket, ObjectAction action) {
        authorization.requireAuthorized(actor, new ObjectAuthorizationRequest("ticket", ticket.id(), action,
            Map.of("requesterIamUserId", ticket.requester().iamUserId(), "serviceCatalogItemId", ticket.serviceCatalogItem().id())));
    }
    private String code(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Processing details code is invalid");
        return normalized;
    }
    private String text(String value, int max, boolean multiline) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!multiline && normalized.chars().anyMatch(ch -> ch == '\r' || ch == '\n' || ch == '\t')) throw new IllegalArgumentException("Processing details text is invalid");
        if (normalized.length() > max) throw new IllegalArgumentException("Processing details text is too long");
        return normalized;
    }
    private String requestId() { String id = MDC.get("requestId"); return id == null ? "system" : id; }
}
