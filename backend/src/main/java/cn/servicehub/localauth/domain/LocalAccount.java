package cn.servicehub.localauth.domain;

import java.time.Instant;

/** Password hashes never leave the local-auth application boundary. */
public record LocalAccount(String id, String loginName, String normalizedLoginName, String passwordHash,
                           String displayName, String organizationId, boolean enabled, int failedLoginCount,
                           Instant lockedUntil, Instant passwordChangedAt, long sessionVersion, long version,
                           String createdBy, String updatedBy, Instant createdAt, Instant updatedAt) {
    public LocalAccount {
        if (id == null || id.isBlank() || normalizedLoginName == null || normalizedLoginName.isBlank()) {
            throw new IllegalArgumentException("Local account identity is required");
        }
        if (passwordHash == null || !passwordHash.startsWith("{bcrypt}")) {
            throw new IllegalArgumentException("A bcrypt password hash is required");
        }
    }
}
