package cn.servicehub.ticket.application;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deployment-owned signing settings for opaque ticket cursors. */
@ConfigurationProperties(prefix = "servicehub.ticket.pagination")
public record TicketPaginationProperties(String cursorSigningKey, Duration cursorTtl) {
    public TicketPaginationProperties {
        if (cursorSigningKey == null || cursorSigningKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Ticket cursor signing key must contain at least 32 UTF-8 bytes");
        }
        if (cursorTtl == null || cursorTtl.isNegative() || cursorTtl.isZero() || cursorTtl.compareTo(Duration.ofHours(24)) > 0) {
            throw new IllegalArgumentException("Ticket cursor TTL must be between 1 millisecond and 24 hours");
        }
    }
}
