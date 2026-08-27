package cn.servicehub.access.domain;

import java.time.Instant;
import java.util.Set;

/** No password, token, editable name or organization data belongs to this local authorization record. */
public record BackofficeAccess(String iamUserId, boolean enabled, Set<String> roleCodes,
                               Set<BackofficeDataScope> dataScopes, long version, Instant updatedAt) {
    public BackofficeAccess {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        dataScopes = dataScopes == null ? Set.of() : Set.copyOf(dataScopes);
    }
}
