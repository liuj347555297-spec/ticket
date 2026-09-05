package cn.servicehub.localauth.domain;

import java.time.Instant;

/** Narrow, local-account-only write port; it is not exposed as a general IAM mutation API. */
public interface LocalAccountProjectionWriter {
    boolean activeOrganizationExists(String organizationId);
    void ensureLocalOrganization(String organizationId, String organizationName, Instant occurredAt);
    void upsert(String accountId, String loginName, String displayName, String organizationId,
                boolean active, long sourceVersion, Instant occurredAt);
}
