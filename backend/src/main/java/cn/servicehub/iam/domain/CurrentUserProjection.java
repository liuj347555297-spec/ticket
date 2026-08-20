package cn.servicehub.iam.domain;

import java.util.List;

/** Authenticated identity joined with server-derived platform roles and data-scope summaries. */
public record CurrentUserProjection(IamUserProjection user, List<String> roles, List<DataScopeSummary> dataScopes) {
    public CurrentUserProjection {
        roles = roles == null ? List.of() : List.copyOf(roles);
        dataScopes = dataScopes == null ? List.of() : List.copyOf(dataScopes);
    }
}
