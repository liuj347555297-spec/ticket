package cn.servicehub.access.web;

import cn.servicehub.access.application.BackofficeAccessCommand;
import cn.servicehub.access.application.BackofficeAccessService;
import cn.servicehub.access.domain.BackofficeDataScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform administrators manage only local entitlement records; IAM identity data remains read-only. */
@RestController
@RequestMapping("/api/v1/admin/backoffice-access")
public class BackofficeAccessController {
    private final BackofficeAccessService service;
    public BackofficeAccessController(BackofficeAccessService service) { this.service = service; }

    @GetMapping("/{iamUserId}")
    BackofficeAccessResponse get(@PathVariable @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String iamUserId) {
        return BackofficeAccessResponse.from(service.get(iamUserId));
    }

    @PutMapping("/{iamUserId}")
    BackofficeAccessResponse replace(@PathVariable @Pattern(regexp = "^[A-Za-z0-9._:-]{1,128}$") String iamUserId,
                                     @Valid @RequestBody BackofficeAccessRequest request) {
        Set<BackofficeDataScope> scopes = request.dataScopes().stream()
            .map(scope -> new BackofficeDataScope(scope.scopeType(), scope.scopeId())).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return BackofficeAccessResponse.from(service.replace(iamUserId,
            new BackofficeAccessCommand(request.enabled(), request.roleCodes(), scopes, request.expectedVersion())));
    }
}
