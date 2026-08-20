package cn.servicehub.iam.domain;

import java.time.Instant;
import java.util.List;

/**
 * Local immutable-at-request-time view of an IAM user. Credentials and IAM access tokens are
 * intentionally absent; write operations belong exclusively to IAM synchronisation adapters.
 */
public record IamUserProjection(
    String iamUserId,
    String loginName,
    String displayName,
    boolean active,
    OrganizationSummary organization,
    List<PositionSummary> positions,
    String sourceSystem,
    String sourceVersion,
    Instant syncedAt) {
    public IamUserProjection {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
