package cn.servicehub.iam.application;

import cn.servicehub.iam.domain.CurrentUserProjection;
import cn.servicehub.iam.domain.DataScopeSummary;
import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class IamProjectionService {
    private static final Set<String> PLATFORM_ROLES = Set.of(
        "REQUESTER", "FIRST_LINE_SUPPORT", "SECOND_LINE_SUPPORT", "APPROVER", "SERVICE_MANAGER", "SLA_MANAGER", "PLATFORM_ADMIN", "AUDITOR");
    private static final Set<String> DATA_SCOPE_TYPES = Set.of("ORGANIZATION", "SERVICE", "QUEUE", "CONFIGURATION_ITEM");
    private static final Pattern DATA_SCOPE_AUTHORITY = Pattern.compile("^DATA_SCOPE_(ORGANIZATION|SERVICE|QUEUE|CONFIGURATION_ITEM):([A-Za-z0-9._:-]{1,128})$");

    private final IamUserProjectionRepository projectionRepository;
    private final CurrentUserProvider currentUserProvider;

    public IamProjectionService(IamUserProjectionRepository projectionRepository, CurrentUserProvider currentUserProvider) {
        this.projectionRepository = projectionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public CurrentUserProjection currentUserProjection() {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        IamUserProjection projection = projectionRepository.findActiveByIamUserId(currentUser.iamUserId())
            .orElseThrow(IamProjectionUnavailableException::new);
        return new CurrentUserProjection(projection, roles(currentUser), dataScopes(currentUser));
    }

    public IamUserProjection requireActiveProjection(String iamUserId) {
        return projectionRepository.findActiveByIamUserId(iamUserId).orElseThrow(IamProjectionUnavailableException::new);
    }

    private List<String> roles(CurrentUser currentUser) {
        return currentUser.authorities().stream()
            .filter(authority -> authority.startsWith("ROLE_"))
            .map(authority -> authority.substring("ROLE_".length()))
            .filter(PLATFORM_ROLES::contains)
            .sorted()
            .toList();
    }

    private List<DataScopeSummary> dataScopes(CurrentUser currentUser) {
        return currentUser.authorities().stream()
            .map(DATA_SCOPE_AUTHORITY::matcher)
            .filter(Matcher::matches)
            .map(matcher -> new DataScopeSummary(matcher.group(1), matcher.group(2)))
            .filter(scope -> DATA_SCOPE_TYPES.contains(scope.scopeType()))
            .distinct()
            .sorted(Comparator.comparing(DataScopeSummary::scopeType).thenComparing(DataScopeSummary::scopeId))
            .toList();
    }
}
