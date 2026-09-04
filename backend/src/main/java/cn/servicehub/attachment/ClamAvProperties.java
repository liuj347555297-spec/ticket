package cn.servicehub.attachment;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicehub.attachment.clamav")
public record ClamAvProperties(
        boolean enabled,
        String host,
        List<String> allowedHosts,
        int port,
        Duration connectTimeout,
        Duration readTimeout,
        Duration totalTimeout,
        int chunkSizeBytes,
        int maxResponseBytes) {

    public ClamAvProperties {
        host = normalizeHost(host);
        allowedHosts = allowedHosts == null ? List.of() : allowedHosts.stream()
                .map(ClamAvProperties::normalizeHost)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (enabled && host.isBlank()) {
            throw new IllegalArgumentException("ClamAV host is required when scanning is enabled");
        }
        if (allowedHosts.size() > 16 || (enabled && !allowedHosts.contains(host))) {
            throw new IllegalArgumentException("ClamAV host must exactly match the managed allow-list");
        }
        if (allowedHosts.stream().anyMatch(value -> !value.matches("^[a-z0-9.:-]+$"))) {
            throw new IllegalArgumentException("ClamAV allow-list contains an invalid host");
        }
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("ClamAV port is invalid");
        }
        requirePositive(connectTimeout, "connect timeout");
        requirePositive(readTimeout, "read timeout");
        requirePositive(totalTimeout, "total timeout");
        if (totalTimeout.compareTo(connectTimeout) < 0 || totalTimeout.compareTo(readTimeout) < 0) {
            throw new IllegalArgumentException("ClamAV total timeout must cover connect and read timeout");
        }
        if (chunkSizeBytes < 1 || chunkSizeBytes > 1024 * 1024) {
            throw new IllegalArgumentException("ClamAV chunk size must be between 1 byte and 1 MiB");
        }
        if (maxResponseBytes < 16 || maxResponseBytes > 64 * 1024) {
            throw new IllegalArgumentException("ClamAV response limit must be between 16 bytes and 64 KiB");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("ClamAV " + name + " must be positive");
        }
    }

    private static String normalizeHost(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
