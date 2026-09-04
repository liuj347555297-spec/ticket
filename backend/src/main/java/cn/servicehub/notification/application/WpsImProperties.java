package cn.servicehub.notification.application;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WPS application metadata. The secret is deliberately not a property: deployments provide only
 * a managed secret reference, while the provider-specific client remains disabled until its
 * reviewed API contract is implemented.
 */
@ConfigurationProperties(prefix = "servicehub.notification.wps-im")
public record WpsImProperties(boolean enabled, String appId, String endpoint, String secretReference,
                              String platformBaseUrl, String templateRef, List<String> allowedHosts,
                              long connectTimeoutMs, long requestTimeoutMs) {
    public boolean readyForDelivery() {
        if (!enabled || blank(appId) || !appId.matches("^[A-Za-z0-9._:-]{1,128}$") || blank(endpoint) || blank(secretReference)
            || !secretReference.matches("^[^\\p{Cntrl}]{1,256}$") || blank(platformBaseUrl)
            || blank(templateRef) || !templateRef.matches("^[A-Za-z0-9._:-]{1,128}$")
            || connectTimeoutMs < 1000 || connectTimeoutMs > 30_000 || requestTimeoutMs < 1000 || requestTimeoutMs > 60_000) return false;
        try {
            URI provider = URI.create(endpoint);
            URI platform = URI.create(platformBaseUrl);
            return "https".equalsIgnoreCase(provider.getScheme()) && provider.getHost() != null && provider.getUserInfo() == null
                && provider.getQuery() == null && provider.getFragment() == null
                && allowedProviderHosts().contains(provider.getHost().toLowerCase(Locale.ROOT))
                && "https".equalsIgnoreCase(platform.getScheme()) && platform.getHost() != null && platform.getUserInfo() == null
                && platform.getQuery() == null && platform.getFragment() == null;
        } catch (IllegalArgumentException ignored) { return false; }
    }
    WpsImManagedChannelConfiguration managedConfiguration() {
        if (!readyForDelivery()) throw new ExternalMessageDeliveryException("WPS_IM_NOT_CONFIGURED", false);
        return new WpsImManagedChannelConfiguration(appId, endpoint, secretReference, platformBaseUrl, templateRef,
            allowedProviderHosts(), connectTimeoutMs, requestTimeoutMs);
    }
    public Set<String> allowedProviderHosts() {
        if (allowedHosts == null) return Set.of();
        return allowedHosts.stream().filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
