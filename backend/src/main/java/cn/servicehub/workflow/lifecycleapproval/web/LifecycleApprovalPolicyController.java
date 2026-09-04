package cn.servicehub.workflow.lifecycleapproval.web;

import cn.servicehub.workflow.lifecycleapproval.application.LifecycleApprovalPolicyService;
import cn.servicehub.workflow.lifecycleapproval.domain.LifecycleApprovalPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@Validated
@RequestMapping("/api/v1/admin/workflow/lifecycle-approval-policies")
public class LifecycleApprovalPolicyController {
    private final LifecycleApprovalPolicyService service;
    public LifecycleApprovalPolicyController(LifecycleApprovalPolicyService service) { this.service=service; }
    @GetMapping public List<LifecycleApprovalPolicy> list(){ return service.list(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public LifecycleApprovalPolicy create(@Valid @RequestBody LifecycleApprovalPolicyRequest request){ return service.create(command(request)); }
    @PutMapping("/{id}") public LifecycleApprovalPolicy update(@PathVariable @Pattern(regexp="^[0-9a-fA-F-]{36}$") String id,@Valid @RequestBody LifecycleApprovalPolicyRequest request){ return service.updateDraft(id,command(request)); }
    @PostMapping("/{id}/publish") public LifecycleApprovalPolicy publish(@PathVariable @Pattern(regexp="^[0-9a-fA-F-]{36}$") String id,@RequestParam @Min(0) long expectedVersion){ return service.publish(id,expectedVersion); }
    @PostMapping("/{id}/retire") public LifecycleApprovalPolicy retire(@PathVariable @Pattern(regexp="^[0-9a-fA-F-]{36}$") String id,@RequestParam @Min(0) long expectedVersion){ return service.retire(id,expectedVersion); }
    private static LifecycleApprovalPolicyService.PolicyCommand command(LifecycleApprovalPolicyRequest r){return new LifecycleApprovalPolicyService.PolicyCommand(r.name(),r.action(),r.serviceCatalogItemId(),r.priority(),r.candidateRoles(),r.decisionMode(),r.approvalThresholdPercent(),r.timeoutMinutes(),r.timeoutPolicyVersion(),r.escalationPolicyVersion(),r.expectedVersion());}
}
