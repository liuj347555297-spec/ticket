package cn.servicehub.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Transitional sink; replace with append-only persistent storage before production use. */
@Component
public class LoggingAuditEventPublisher implements AuditEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuditEventPublisher.class);

    @Override
    public void publish(AuditEvent event) {
        log.info("audit action={} resourceType={} resourceId={} actor={} requestId={}", event.action(),
            event.resourceType(), event.resourceId(), event.actorIamUserId(), event.requestId());
    }
}
