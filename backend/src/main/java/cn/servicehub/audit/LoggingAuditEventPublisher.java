package cn.servicehub.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Local development/test sink. MySQL deployments use append-only persistence instead. */
@Component
@org.springframework.context.annotation.Profile("!mysql")
public class LoggingAuditEventPublisher implements AuditEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuditEventPublisher.class);

    @Override
    public void publish(AuditEvent event) {
        log.info("audit action={} resourceType={} resourceId={} actor={} requestId={}", event.action(),
            event.resourceType(), event.resourceId(), event.actorIamUserId(), event.requestId());
    }
}
