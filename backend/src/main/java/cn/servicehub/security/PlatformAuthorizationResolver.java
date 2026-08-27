package cn.servicehub.security;

import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Resolves effective platform authority from a verified IAM subject on every domain request. */
@Component
public class PlatformAuthorizationResolver {
    private final IamUserProjectionRepository iamUsers;
    private final BackofficeAccessRepository access;
    public PlatformAuthorizationResolver(IamUserProjectionRepository iamUsers, BackofficeAccessRepository access) { this.iamUsers = iamUsers; this.access = access; }

    public CurrentUser resolve(String iamUserId, String authenticationType) {
        iamUsers.findActiveByIamUserId(iamUserId).orElseThrow(() -> new AccessDeniedException("Active IAM projection is required"));
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_REQUESTER");
        access.findByIamUserId(iamUserId).filter(cn.servicehub.access.domain.BackofficeAccess::enabled).ifPresent(value -> {
            authorities.addAll(value.roleCodes());
            value.dataScopes().forEach(scope -> authorities.add("DATA_SCOPE_" + scope.scopeType() + ":" + scope.scopeId()));
        });
        return new CurrentUser(iamUserId, authorities, authenticationType);
    }
}
