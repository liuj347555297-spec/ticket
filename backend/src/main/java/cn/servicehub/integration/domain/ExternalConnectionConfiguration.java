package cn.servicehub.integration.domain;

import java.time.Instant;
import java.util.List;

/**
 * Connection metadata only. {@code secretRef} is an opaque reference to a deployment secret
 * manager; credential values must never be persisted in this application or returned by an API.
 */
public record ExternalConnectionConfiguration(String code, String displayName, ExternalSystemType systemType,
                                              String trustedBaseUrl, String secretRef, boolean enabled,
                                              int timeoutMs, int rateLimitPerMinute,
                                              List<String> allowedCallbackSourceIps, Instant updatedAt) {
    public ExternalConnectionConfiguration {
        allowedCallbackSourceIps = allowedCallbackSourceIps == null ? List.of() : List.copyOf(allowedCallbackSourceIps);
    }
}
