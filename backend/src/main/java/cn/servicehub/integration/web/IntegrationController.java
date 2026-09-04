package cn.servicehub.integration.web;

import cn.servicehub.integration.application.IntegrationService;
import cn.servicehub.integration.domain.ConfigurationItem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Browser-facing projections omit external base URLs, secret references and all raw alert payloads. */
@RestController
@Validated
@RequestMapping("/api/v1")
public class IntegrationController {
    private final IntegrationService service;
    public IntegrationController(IntegrationService service) { this.service=service; }
    @GetMapping("/integrations/connections")
    List<IntegrationService.ConnectionSummary> connections() { return service.connections(); }
    @GetMapping("/integrations/operations-overview")
    IntegrationService.OperationsOverview overview() { return service.operationsOverview(); }
    @GetMapping("/integrations/configuration-items")
    ConfigurationItemPage configurationItems(@RequestParam @Pattern(regexp="[A-Za-z0-9._:-]{1,128}") String organizationId) { return new ConfigurationItemPage(service.configurationItems(organizationId)); }
    @GetMapping("/tickets/{ticketId}/configuration-items")
    ConfigurationItemPage ticketConfigurationItems(@PathVariable @Pattern(regexp="TKT-[0-9]{8}-[0-9]{6}") String ticketId) { return new ConfigurationItemPage(service.ticketConfigurationItems(ticketId)); }
    @PostMapping("/tickets/{ticketId}/integrations/deep-links")
    IntegrationService.DeepLinkSummary deepLink(@PathVariable @Pattern(regexp="TKT-[0-9]{8}-[0-9]{6}") String ticketId, @Valid @RequestBody DeepLinkRequest request) { return service.deepLink(ticketId,request.systemCode(),request.resourceType(),request.resourceId()); }
    /** A signed callback is unauthenticated at the web layer but verifies source IP, HMAC, time and nonce before parsing. */
    @PostMapping(value="/integrations/alerts/{sourceCode}", consumes="application/json")
    @ResponseStatus(HttpStatus.ACCEPTED)
    AlertReceipt receiveAlert(@PathVariable @Pattern(regexp="[A-Z0-9_-]{2,40}") String sourceCode,
                              @RequestHeader(value="X-Integration-Timestamp", required=false) String timestamp,
                              @RequestHeader(value="X-Integration-Nonce", required=false) String nonce,
                              @RequestHeader(value="X-Integration-Signature", required=false) String signature,
                              @RequestBody String body, HttpServletRequest request) {
        var received=service.receiveAlert(sourceCode,request.getRemoteAddr(),timestamp,nonce,signature,body);
        var alert=received.alert();
        return new AlertReceipt(alert.id(),alert.status(),received.idempotencyStatus(),received.recommendation(),alert.ticketId());
    }
    public record ConfigurationItemPage(List<ConfigurationItem> items) { }
    public record DeepLinkRequest(@NotBlank @Pattern(regexp="[A-Z0-9_-]{2,40}") String systemCode,
                                  @NotBlank @Pattern(regexp="CONFIGURATION_ITEM|ALERT|TRACE") String resourceType,
                                  @NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{1,160}") String resourceId) { }
    public record AlertReceipt(String alertId,String status,String idempotencyStatus,String recommendation,String ticketId) { }
}
