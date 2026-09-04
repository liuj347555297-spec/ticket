package cn.servicehub.security;

import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
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
        var projection = iamUsers.findActiveByIamUserId(iamUserId).orElseThrow(() -> new AccessDeniedException("Active IAM projection is required"));
        var backoffice = access.findByIamUserId(iamUserId).filter(cn.servicehub.access.domain.BackofficeAccess::enabled).orElse(null);
        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_REQUESTER");
        if (backoffice != null) {
            var value = backoffice;
            authorities.addAll(value.roleCodes());
            value.dataScopes().forEach(scope -> authorities.add("DATA_SCOPE_" + scope.scopeType() + ":" + scope.scopeId()));
        }
        return new CurrentUser(iamUserId, authorities, authenticationType, true,
            digest(projection.sourceSystem(), projection.sourceVersion(), projection.syncedAt().toString(), backoffice, authorities));
    }

    private static String digest(String sourceSystem, String sourceVersion, String syncedAt,
                                 cn.servicehub.access.domain.BackofficeAccess backoffice, Set<String> authorities) {
        try {
            String accessVersion = backoffice == null ? "none" : backoffice.version() + ":" + backoffice.updatedAt();
            String canonical = sourceSystem + "\n" + sourceVersion + "\n" + syncedAt + "\n" + accessVersion + "\n"
                + authorities.stream().sorted(Comparator.naturalOrder()).reduce("", (a, b) -> a + b + "\n");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Authorization scope digest is unavailable", exception);
        }
    }
}
