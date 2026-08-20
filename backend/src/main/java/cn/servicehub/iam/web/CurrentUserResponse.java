package cn.servicehub.iam.web;

import cn.servicehub.iam.domain.CurrentUserProjection;
import cn.servicehub.iam.domain.DataScopeSummary;
import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.OrganizationSummary;
import java.util.List;

/** API response intentionally excludes employee number, contacts, credentials and tokens. */
public record CurrentUserResponse(User user, Authorization authorization) {
    public static CurrentUserResponse from(CurrentUserProjection projection) {
        IamUserProjection user = projection.user();
        return new CurrentUserResponse(new User(user.iamUserId(), user.loginName(), user.displayName(),
            user.active() ? "ACTIVE" : "DISABLED", Organization.from(user.organization())),
            new Authorization(projection.roles(), projection.dataScopes()));
    }

    public record User(String iamUserId, String loginName, String displayName, String status, Organization organization) {
    }

    public record Organization(String iamOrganizationId, String name) {
        static Organization from(OrganizationSummary organization) {
            return new Organization(organization.iamOrganizationId(), organization.name());
        }
    }

    public record Authorization(List<String> roles, List<DataScopeSummary> dataScopes) {
        public Authorization {
            roles = List.copyOf(roles);
            dataScopes = List.copyOf(dataScopes);
        }
    }
}
