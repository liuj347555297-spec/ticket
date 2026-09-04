package cn.servicehub.catalog.config;

import java.time.Instant;

public record FormPublicationRequest(String id, String catalogItemId, long requestedVersion, String reason,
                                     String applicantIamUserId, FormConfigurationStatus status, Instant requestedAt,
                                     String decidedByIamUserId, Instant decidedAt) { }
