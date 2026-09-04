package cn.servicehub.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-controlled limits for the durable external-message dispatcher. */
@ConfigurationProperties(prefix = "servicehub.notification.dispatch")
public record NotificationDispatchProperties(int batchSize, int maxAttempts, long retryBaseDelayMs, long deliveringLeaseMs,
                                             long fixedDelayMs) {
    public NotificationDispatchProperties {
        if (batchSize < 1 || batchSize > 200) throw new IllegalArgumentException("Notification dispatch batch size must be 1..200");
        if (maxAttempts < 1 || maxAttempts > 20) throw new IllegalArgumentException("Notification dispatch max attempts must be 1..20");
        if (retryBaseDelayMs < 1000 || retryBaseDelayMs > 3_600_000 || deliveringLeaseMs < 10000 || fixedDelayMs < 1000) {
            throw new IllegalArgumentException("Notification dispatch timing values are too small");
        }
    }
}
