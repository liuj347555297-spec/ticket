package cn.servicehub.iam.infrastructure;

import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.iam.domain.IamRoleProjectionRepository;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development/test projection of platform-managed backoffice roles. */
@Repository
@Profile("!mysql")
public class InMemoryIamRoleProjectionRepository implements IamRoleProjectionRepository {
    private final BackofficeAccessRepository access;
    public InMemoryIamRoleProjectionRepository(BackofficeAccessRepository access) { this.access = access; }

    @Override
    public Set<String> findActiveRoleCodes(String iamUserId) {
        return access.findByIamUserId(iamUserId).filter(cn.servicehub.access.domain.BackofficeAccess::enabled)
            .map(cn.servicehub.access.domain.BackofficeAccess::roleCodes).orElse(Set.of());
    }

    @Override
    public java.util.List<String> findActiveIamUserIdsByRoleCodes(Set<String> roleCodes) {
        return access.findEnabledIamUserIdsByRoleCodes(roleCodes);
    }
}
