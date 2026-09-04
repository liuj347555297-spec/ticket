package cn.servicehub.sla.domain;

import java.time.Instant;

public interface SlaEscalationEventRepository {
    boolean appendIfAbsent(String ticketId, long targetVersion, String eventCode, SlaRiskLevel riskLevel, Instant occurredAt);
}
