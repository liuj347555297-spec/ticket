package cn.servicehub.sla.web;

import cn.servicehub.sla.application.SlaPolicyCommand;
import cn.servicehub.sla.application.SlaService;
import cn.servicehub.sla.domain.SlaPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Policy writes are intentionally narrow: managers/admins only and always audit-published by SlaService. */
@RestController
@RequestMapping({"/api/v1/admin/sla/policies", "/api/v1/admin/sla-policies"})
public class SlaPolicyController {
    private final SlaService service;
    public SlaPolicyController(SlaService service) { this.service = service; }
    @GetMapping
    java.util.List<SlaPolicy> list() { return service.listPolicies(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    SlaPolicy create(@Valid @RequestBody SlaPolicyRequest request) { return service.savePolicy(null, command(request)); }
    @PutMapping("/{policyId}")
    SlaPolicy update(@PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String policyId, @Valid @RequestBody SlaPolicyRequest request) { return service.savePolicy(policyId, command(request)); }
    private static SlaPolicyCommand command(SlaPolicyRequest r) { return new SlaPolicyCommand(r.name(), r.serviceCatalogItemId(), r.priority(), r.organizationScopeId(), r.responseTargetMinutes(), r.resolutionTargetMinutes(), r.calendarKey(), r.pauseStatuses(), r.active(), r.expectedVersion()); }
}
