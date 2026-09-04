package cn.servicehub.servicesystem.domain;

import java.time.Instant;

/** Platform-owned routing metadata; CI is a read-only reference, never a CMDB replica. */
public record ServiceSystem(String code, String name, String configurationItemId, String ownerIamUserId,
                            String owningOrganizationId, ServiceSystemStatus status, long version,
                            String changeReason, String createdByIamUserId, String updatedByIamUserId,
                            Instant createdAt, Instant updatedAt, Instant publishedAt) { }
