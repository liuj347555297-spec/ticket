package cn.servicehub.audit;

public interface AuditEventPublisher {
    void publish(AuditEvent event);
}
