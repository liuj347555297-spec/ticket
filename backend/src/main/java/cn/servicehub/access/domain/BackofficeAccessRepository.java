package cn.servicehub.access.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BackofficeAccessRepository {
    Optional<BackofficeAccess> findByIamUserId(String iamUserId);
    List<String> findEnabledIamUserIdsByRoleCodes(Set<String> roleCodes);
    long countEnabledUsersWithRole(String roleCode);
    BackofficeAccess save(BackofficeAccess access, long expectedVersion, String actorIamUserId);
}
