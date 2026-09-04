package cn.servicehub.integration.application;

import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.integration.domain.ConfigurationItem;
import cn.servicehub.integration.domain.ConfigurationItemRepository;
import cn.servicehub.integration.domain.ExternalConnectionConfiguration;
import cn.servicehub.integration.domain.ExternalConnectionConfigurationRepository;
import cn.servicehub.integration.domain.ExternalSystemType;
import cn.servicehub.integration.domain.NormalizedAlert;
import cn.servicehub.integration.domain.NormalizedAlertRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import cn.servicehub.security.ObjectAction;
import cn.servicehub.security.ObjectAuthorizationRequest;
import cn.servicehub.security.ObjectAuthorizationService;
import cn.servicehub.ticket.application.TicketNotFoundException;
import cn.servicehub.ticket.domain.Ticket;
import cn.servicehub.ticket.domain.TicketRepository;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Boundary for CMDB/monitoring/log/APM integrations. No real external client is invoked by this service. */
@Service
public class IntegrationService {
    private static final Set<String> OPERATIONS_ROLES = Set.of("ROLE_PLATFORM_ADMIN", "ROLE_SERVICE_MANAGER", "ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT", "ROLE_AUDITOR");
    private static final int MAX_CALLBACK_BODY_BYTES = 64 * 1024;
    private final ExternalConnectionConfigurationRepository connections; private final ConfigurationItemRepository configurationItems;
    private final NormalizedAlertRepository alerts; private final List<ExternalAlertAdapterPort> alertAdapters;
    private final InboundAlertSignatureVerifier signatures; private final TicketRepository tickets;
    private final CurrentUserProvider users; private final ObjectAuthorizationService authorization; private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();
    public IntegrationService(ExternalConnectionConfigurationRepository connections, ConfigurationItemRepository configurationItems,
                              NormalizedAlertRepository alerts, List<ExternalAlertAdapterPort> alertAdapters,
                              InboundAlertSignatureVerifier signatures, TicketRepository tickets, CurrentUserProvider users,
                              ObjectAuthorizationService authorization, AuditEventPublisher audit) {
        this.connections=connections; this.configurationItems=configurationItems; this.alerts=alerts; this.alertAdapters=alertAdapters;
        this.signatures=signatures; this.tickets=tickets; this.users=users; this.authorization=authorization; this.audit=audit;
    }
    public List<ConnectionSummary> connections() { CurrentUser actor=users.requireCurrentUser(); requireOperations(actor); return connections.findAll().stream().map(this::connectionSummary).toList(); }
    public OperationsOverview operationsOverview() { CurrentUser actor=users.requireCurrentUser(); requireOperations(actor); return new OperationsOverview("当前 IAM 授权组织与配置项范围", connections.findAll().stream().map(this::health).toList(), alerts.findRecent(50).stream().map(this::alertSummary).toList()); }
    public List<ConfigurationItem> configurationItems(String organizationId) {
        CurrentUser actor=users.requireCurrentUser(); if (organizationId==null || !organizationId.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("organizationId is invalid");
        requireConfigurationScope(actor, organizationId, null); return configurationItems.findByOrganizationId(organizationId);
    }
    public List<ConfigurationItem> ticketConfigurationItems(String ticketId) { CurrentUser actor=users.requireCurrentUser(); Ticket ticket=ticket(ticketId); requireTicketRead(actor,ticket); return configurationItems.findByTicketId(ticketId); }
    /** Validates source-owned CI IDs before a ticket is stored, preventing partial ticket creation. */
    public void validateConfigurationItemIds(List<String> ids, String requesterOrganizationId) {
        if (requesterOrganizationId == null || !requesterOrganizationId.matches("[A-Za-z0-9._:-]{1,128}")) throw new IllegalArgumentException("Requester organization is invalid");
        for (String id : ids) {
            ConfigurationItem ci=configurationItems.findById(id).orElseThrow(() -> new IllegalArgumentException("Configuration item is not available"));
            if (!ci.sourceCode().equals("CMDB") || !requesterOrganizationId.equals(ci.organizationId())) throw new IllegalArgumentException("Configuration item is outside requester organization");
        }
    }
    /** Runs after a server-authorized ticket has been persisted. Browser input cannot create CIs. */
    public void associateTicketConfigurationItems(Ticket ticket) {
        List<String> ids=ticket.relatedConfigurationItemIds();
        validateConfigurationItemIds(ids, ticket.requester().organizationId());
        configurationItems.replaceTicketAssociations(ticket.id(), ids);
        if (!ids.isEmpty()) audit(users.currentUser().orElse(new CurrentUser("system",Set.of(),"system")), "TICKET_CI_ASSOCIATED", ticket.id(), Map.of("count",String.valueOf(ids.size())));
    }
    public DeepLinkSummary deepLink(String ticketId, String systemCode, String resourceType, String resourceId) {
        CurrentUser actor=users.requireCurrentUser(); Ticket ticket=ticket(ticketId); requireTicketRead(actor,ticket);
        if (!Set.of("CONFIGURATION_ITEM", "ALERT", "TRACE").contains(resourceType) || resourceId==null || !resourceId.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException("Link resource is invalid");
        ExternalConnectionConfiguration config=connections.findByCode(systemCode).filter(ExternalConnectionConfiguration::enabled).orElseThrow(() -> new AccessDeniedException("Integration is not enabled"));
        if (config.systemType()!=ExternalSystemType.LOG_PLATFORM && config.systemType()!=ExternalSystemType.APM) throw new AccessDeniedException("Integration does not support deep links");
        URI base=trustedHttpsBase(config.trustedBaseUrl());
        String url=base.toString().replaceAll("/$", "") + "/servicehub/" + resourceType.toLowerCase(java.util.Locale.ROOT) + "/" + URLEncoder.encode(resourceId, StandardCharsets.UTF_8).replace("+", "%20");
        audit(actor,"INTEGRATION_DEEP_LINK_CREATED",ticketId,Map.of("systemCode",config.code(),"resourceType",resourceType,"resourceId",resourceId));
        return new DeepLinkSummary(config.code(),config.displayName(),resourceType,resourceId,url);
    }
    /** Called on a permit-listed endpoint only; verification happens before parsing or processing body content. */
    public ReceivedAlert receiveAlert(String sourceCode, String remoteAddress, String timestamp, String nonce, String signature, String body) {
        if (body == null || body.getBytes(StandardCharsets.UTF_8).length > MAX_CALLBACK_BODY_BYTES) throw new IntegrationSecurityException();
        ExternalConnectionConfiguration config=connections.findByCode(sourceCode).orElseThrow(IntegrationSecurityException::new);
        signatures.verify(config,remoteAddress,timestamp,nonce,signature,body);
        ExternalAlertAdapterPort adapter=alertAdapters.stream().filter(port -> port.supports(sourceCode)).findFirst().orElseThrow(IntegrationSecurityException::new);
        ExternalAlertAdapterPort.AlertInput input=adapter.normalize(body);
        if (input.configurationItemId()!=null && configurationItems.findById(input.configurationItemId()).isEmpty()) throw new IllegalArgumentException("Unknown configuration item");
        NormalizedAlert existing=alerts.findBySourceAndEventId(sourceCode,input.sourceEventId()).orElse(null); if(existing!=null) return new ReceivedAlert(existing,"DEDUPLICATED",recommendationFor(existing));
        NormalizedAlert attempted=new NormalizedAlert("ALT-"+UUID.randomUUID(),sourceCode,input.sourceEventId(),input.fingerprint(),input.severity(),input.title(),input.configurationItemId(),"RECEIVED","CREATED",null,input.occurredAt(),clock.instant());
        NormalizedAlert saved=alerts.save(attempted);
        if (!attempted.id().equals(saved.id())) return new ReceivedAlert(saved,"DEDUPLICATED",recommendationFor(saved));
        audit(new CurrentUser("integration:"+sourceCode,Set.of(),"signed-callback"),"EXTERNAL_ALERT_RECEIVED",saved.id(),Map.of("sourceCode",sourceCode,"severity",saved.severity(),"hasConfigurationItem",String.valueOf(saved.configurationItemId()!=null)));
        return new ReceivedAlert(saved,"CREATED",recommendationFor(saved));
    }
    private ConnectionSummary connectionSummary(ExternalConnectionConfiguration c) { return new ConnectionSummary(c.code(),c.displayName(),c.systemType().name(),c.enabled(),c.timeoutMs(),c.rateLimitPerMinute(),c.secretRef()!=null&&!c.secretRef().isBlank(),c.updatedAt()); }
    private ConnectionHealth health(ExternalConnectionConfiguration c) { return new ConnectionHealth(c.code(),c.systemType().name(),c.enabled(),c.enabled()?"NOT_CHECKED":"DISABLED",c.timeoutMs(),c.rateLimitPerMinute(),null); }
    private AlertSummary alertSummary(NormalizedAlert a) { String name=a.configurationItemId()==null?null:configurationItems.findById(a.configurationItemId()).map(ConfigurationItem::name).orElse(null); return new AlertSummary(a.id(),a.sourceCode(),a.severity(),a.status(),a.idempotencyStatus(),a.ticketId(),a.configurationItemId(),name,recommendationFor(a),a.occurredAt()); }
    /** Reviewed server rule only. It is a recommendation, never browser-triggered automatic creation. */
    private String recommendationFor(NormalizedAlert alert) { return alert.ticketId()!=null ? "TICKET_ALREADY_LINKED" : ("MONITORING".equals(alert.sourceCode()) && alert.configurationItemId()!=null && Set.of("CRITICAL","HIGH").contains(alert.severity()) ? "REVIEW_INCIDENT_CREATION" : "MANUAL_TRIAGE"); }
    private Ticket ticket(String id) { return tickets.findById(id).orElseThrow(() -> new TicketNotFoundException(id)); }
    private void requireTicketRead(CurrentUser actor,Ticket ticket) { authorization.requireAuthorized(actor,new ObjectAuthorizationRequest("ticket",ticket.id(),ObjectAction.READ,Map.of("requesterIamUserId",ticket.requester().iamUserId(),"serviceCatalogItemId",ticket.serviceCatalogItem().id()))); }
    private void requireOperations(CurrentUser actor) { if(actor.authorities().stream().noneMatch(OPERATIONS_ROLES::contains)) throw new AccessDeniedException("Integration operations is not authorized"); }
    private void requireConfigurationScope(CurrentUser actor,String organizationId,String ciId) { if(actor.authorities().contains("ROLE_PLATFORM_ADMIN")||actor.authorities().contains("ROLE_AUDITOR")) return; if(ciId!=null&&actor.authorities().contains("DATA_SCOPE_CONFIGURATION_ITEM:"+ciId))return; if(actor.authorities().contains("DATA_SCOPE_ORGANIZATION:"+organizationId))return; throw new AccessDeniedException("Configuration item data scope is not authorized"); }
    private static URI trustedHttpsBase(String raw) { try { URI value=URI.create(raw); if(!"https".equalsIgnoreCase(value.getScheme())||value.getHost()==null||value.getUserInfo()!=null||value.getRawQuery()!=null||value.getRawFragment()!=null) throw new IllegalArgumentException(); return value; } catch(RuntimeException e) { throw new AccessDeniedException("Trusted integration URL is invalid"); } }
    private void audit(CurrentUser actor,String action,String id,Map<String,String> attributes) { audit.publish(new AuditEvent(clock.instant(),MDC.get("requestId")==null?"system":MDC.get("requestId"),actor.iamUserId(),action,"integration",id,attributes)); }
    public record ConnectionSummary(String code,String displayName,String systemType,boolean enabled,int timeoutMs,int rateLimitPerMinute,boolean secretConfigured,Instant updatedAt) { }
    public record ConnectionHealth(String code,String systemType,boolean enabled,String healthStatus,int timeoutMs,int rateLimitPerMinute,Instant lastSuccessAt) { }
    public record AlertSummary(String alertId,String sourceCode,String severity,String status,String idempotencyStatus,String ticketId,String configurationItemId,String configurationItemName,String recommendation,Instant occurredAt) { }
    public record OperationsOverview(String scopeLabel,List<ConnectionHealth> connectionHealths,List<AlertSummary> recentAlerts) { }
    public record DeepLinkSummary(String systemCode,String displayName,String resourceType,String resourceId,String url) { }
    public record ReceivedAlert(NormalizedAlert alert, String idempotencyStatus, String recommendation) { }
}
