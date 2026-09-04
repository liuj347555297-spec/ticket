package cn.servicehub.operations.application;

import cn.servicehub.security.CurrentUser;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Resolves only explicit, current organization grants for operational reports and exports. */
@Component
public class OperationsAuthorizationScopeResolver {
    private static final String ORGANIZATION_PREFIX = "DATA_SCOPE_ORGANIZATION:";
    private static final String ORGANIZATION_FAMILY = "DATA_SCOPE_ORGANIZATION";
    private static final Pattern SCOPE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    public Set<String> organizations(CurrentUser user) {
        Set<String> organizations = new LinkedHashSet<>();
        for (String authority : user.authorities()) {
            if (!authority.startsWith(ORGANIZATION_FAMILY)) continue;
            if (!authority.startsWith(ORGANIZATION_PREFIX)) throw invalid();
            String organizationId = authority.substring(ORGANIZATION_PREFIX.length());
            if (!SCOPE_ID.matcher(organizationId).matches() || "*".equals(organizationId)) throw invalid();
            organizations.add(organizationId);
        }
        return Set.copyOf(organizations);
    }

    private static AccessDeniedException invalid() {
        return new AccessDeniedException("Operational organization scope is invalid");
    }
}
