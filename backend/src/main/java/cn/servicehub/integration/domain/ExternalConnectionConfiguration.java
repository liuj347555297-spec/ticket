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

    /**
     * Callback eligibility is deliberately narrower than a connection merely being enabled.
     * It prevents a CMDB or diagnostic-link connection from accidentally becoming a public
     * ingestion endpoint because of a database flag change.
     */
    public boolean supportsSignedAlertCallback() {
        return enabled
            && systemType == ExternalSystemType.MONITORING
            && code != null && code.matches("[A-Z0-9_-]{2,40}")
            && secretRef != null && secretRef.matches("(?:vault|kms|secret)://[A-Za-z0-9._:/-]{1,240}")
            && allowedCallbackSourceIps.stream().allMatch(ExternalConnectionConfiguration::isLiteralIpAddress)
            && !allowedCallbackSourceIps.isEmpty();
    }

    private static boolean isLiteralIpAddress(String value) {
        if (value == null || value.isBlank() || value.length() > 45 || !value.matches("[0-9A-Fa-f:.]+")) return false;
        try {
            return java.net.InetAddress.getByName(value).getHostAddress() != null;
        } catch (java.net.UnknownHostException ignored) {
            return false;
        }
    }
}
