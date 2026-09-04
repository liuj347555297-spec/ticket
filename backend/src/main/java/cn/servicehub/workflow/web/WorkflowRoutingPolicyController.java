package cn.servicehub.workflow.web;

import cn.servicehub.workflow.routing.NodeAssignmentMode;
import cn.servicehub.workflow.routing.NodeAssignmentPolicy;
import cn.servicehub.workflow.routing.WorkflowRoutingPolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/service-catalog/items/{catalogItemId}/workflow-node-policies")
public class WorkflowRoutingPolicyController {
    private final WorkflowRoutingPolicyService service;
    public WorkflowRoutingPolicyController(WorkflowRoutingPolicyService service) { this.service=service; }
    @GetMapping List<NodeAssignmentPolicy> list(@PathVariable @Pattern(regexp="^[A-Za-z0-9_-]{3,64}$") String catalogItemId){return service.list(catalogItemId);}
    @PutMapping("/{nodeKey}") NodeAssignmentPolicy save(@PathVariable @Pattern(regexp="^[A-Za-z0-9_-]{3,64}$") String catalogItemId,@PathVariable @Pattern(regexp="^(accept|processing|user_feedback|closure)$") String nodeKey,@RequestHeader(HttpHeaders.IF_MATCH) @Pattern(regexp="^\"?[0-9]+\"?$") String ifMatch,@Valid @RequestBody Request request){return service.save(catalogItemId,nodeKey,request.mode(),request.queueCode(),request.candidateRoles(),request.enabled(),Long.parseLong(ifMatch.replace("\"","")));}
    public record Request(@NotNull NodeAssignmentMode mode,@Pattern(regexp="^[A-Z][A-Z0-9_-]{1,63}$") String queueCode,@NotNull @Size(min=1,max=3) Set<@NotBlank @Pattern(regexp="^ROLE_(FIRST_LINE_SUPPORT|SECOND_LINE_SUPPORT|SERVICE_MANAGER)$") String> candidateRoles,boolean enabled){}
}
