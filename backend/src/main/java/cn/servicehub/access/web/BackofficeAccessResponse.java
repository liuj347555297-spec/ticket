package cn.servicehub.access.web;

import cn.servicehub.access.application.BackofficeAccessService.AccessView;
import cn.servicehub.access.domain.BackofficeDataScope;
import java.time.Instant;
import java.util.List;

/** Excludes credentials, tokens and IAM source metadata from the administration API. */
public record BackofficeAccessResponse(User user, Access access) {
    static BackofficeAccessResponse from(AccessView view) {
        return new BackofficeAccessResponse(new User(view.user().iamUserId(), view.user().loginName(), view.user().displayName(),
            view.user().organization().iamOrganizationId(), view.user().organization().name()),
            new Access(view.access().enabled(), view.access().roleCodes().stream().sorted().toList(),
                view.access().dataScopes().stream().sorted(java.util.Comparator.comparing(BackofficeDataScope::scopeType).thenComparing(BackofficeDataScope::scopeId))
                    .map(scope -> new DataScope(scope.scopeType(), scope.scopeId())).toList(), view.access().version(), view.access().updatedAt()));
    }
    public record User(String iamUserId, String loginName, String displayName, String organizationIamId, String organizationName) { }
    public record Access(boolean enabled, List<String> roleCodes, List<DataScope> dataScopes, long version, Instant updatedAt) { }
    public record DataScope(String scopeType, String scopeId) { }
}
