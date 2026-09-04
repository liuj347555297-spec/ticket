package cn.servicehub.notification.application;

import java.net.URI;
import java.util.Set;

/**
 * Metadata-only WPS enterprise application configuration. Secrets are a deployment-managed
 * reference, never a value. targetUrl is derived solely from this trusted platform base URL and a
 * ticket ID; it contains no session, SSO or one-time authentication token.
 */
public record WpsImManagedChannelConfiguration(String appId, String endpoint, String secretRef,
                                                String platformBaseUrl, String templateRef, Set<String> allowedHosts,
                                                long connectTimeoutMs, long requestTimeoutMs) {
    public String targetUrl(String ticketId) {
        if (ticketId == null || !ticketId.matches("^TKT-[0-9]{8}-[0-9]{6}$")) throw new IllegalArgumentException("Invalid ticket reference");
        URI base = URI.create(platformBaseUrl);
        if (!base.isAbsolute() || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) throw new IllegalStateException("Managed platform base URL is invalid");
        return base.toString().replaceAll("/+$", "") + "/tickets/" + ticketId;
    }
}
