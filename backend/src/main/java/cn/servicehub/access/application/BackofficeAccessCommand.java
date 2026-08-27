package cn.servicehub.access.application;

import cn.servicehub.access.domain.BackofficeDataScope;
import java.util.Set;

public record BackofficeAccessCommand(boolean enabled, Set<String> roleCodes,
                                      Set<BackofficeDataScope> dataScopes, long expectedVersion) {
    public BackofficeAccessCommand {
        roleCodes = roleCodes == null ? Set.of() : Set.copyOf(roleCodes);
        dataScopes = dataScopes == null ? Set.of() : Set.copyOf(dataScopes);
    }
}
