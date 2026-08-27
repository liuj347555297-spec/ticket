package cn.servicehub.announcement.domain;

import java.time.Instant;

/** Server-owned announcement projection. No browser-supplied audience expansion is trusted. */
public record Announcement(String id, String title, String body, AudienceScope audienceScope,
                           String targetOrganizationIamId, boolean pinned, Instant effectiveFrom,
                           Instant effectiveUntil, String creatorIamUserId, Instant createdAt,
                           Instant updatedAt, long version) {
    public enum AudienceScope { ALL, ORGANIZATION }
}
