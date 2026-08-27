package cn.servicehub.iam.domain;

import java.util.Set;
import java.util.List;

/**
 * Query-only platform-role projection originating in IAM or its approved role mapping.
 * It deliberately exposes no role-grant operation to the service desk application.
 */
public interface IamRoleProjectionRepository {
    Set<String> findActiveRoleCodes(String iamUserId);
    List<String> findActiveIamUserIdsByRoleCodes(Set<String> roleCodes);

    default boolean hasAnyActiveRole(String iamUserId, Set<String> expectedRoleCodes) {
        return findActiveRoleCodes(iamUserId).stream().anyMatch(expectedRoleCodes::contains);
    }
}
