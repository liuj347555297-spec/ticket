package cn.servicehub.access.application;

import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Governs platform-local backoffice entitlements. IAM identities/organizations are never written
 * here; a valid IAM user remains an ordinary requester unless this service enables extra access.
 */
@Service
public class BackofficeAccessService {
    public static final String PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("ROLE_FIRST_LINE_SUPPORT", "ROLE_SECOND_LINE_SUPPORT",
        "ROLE_APPROVER", "ROLE_SERVICE_MANAGER", "ROLE_SLA_MANAGER", "ROLE_AUDITOR", PLATFORM_ADMIN);
    private static final Set<String> SCOPE_TYPES = Set.of("ORGANIZATION", "SERVICE", "QUEUE", "CONFIGURATION_ITEM");
    private static final Pattern IAM_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern SCOPE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private final BackofficeAccessRepository repository;
    private final IamUserProjectionRepository iamUsers;
    private final CurrentUserProvider users;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();

    public BackofficeAccessService(BackofficeAccessRepository repository, IamUserProjectionRepository iamUsers,
                                   CurrentUserProvider users, AuditEventPublisher audit) {
        this.repository = repository; this.iamUsers = iamUsers; this.users = users; this.audit = audit;
    }

    public AccessView get(String iamUserId) {
        CurrentUser actor = requireAdministrator();
        IamUserProjection subject = requireActiveIamUser(iamUserId);
        BackofficeAccess access = repository.findByIamUserId(iamUserId)
            .orElse(new BackofficeAccess(iamUserId, false, Set.of(), Set.of(), 0, null));
        audit("BACKOFFICE_ACCESS_VIEWED", iamUserId, actor, Map.of());
        return new AccessView(subject, access);
    }

    @Transactional
    public AccessView replace(String iamUserId, BackofficeAccessCommand command) {
        CurrentUser actor = requireAdministrator();
        if (!IAM_ID.matcher(iamUserId).matches()) throw new IllegalArgumentException("IAM user ID is invalid");
        if (actor.iamUserId().equals(iamUserId)) throw new AccessDeniedException("Administrators cannot modify their own backoffice access");
        IamUserProjection subject = requireActiveIamUser(iamUserId);
        validate(command);
        BackofficeAccess current = repository.findByIamUserId(iamUserId).orElse(null);
        long actualVersion = current == null ? 0 : current.version();
        if (actualVersion != command.expectedVersion()) throw new BackofficeAccessConflictException();
        boolean removesLastAdmin = current != null && current.enabled() && current.roleCodes().contains(PLATFORM_ADMIN)
            && (!command.enabled() || !command.roleCodes().contains(PLATFORM_ADMIN));
        if (removesLastAdmin && repository.countEnabledUsersWithRole(PLATFORM_ADMIN) <= 1) {
            throw new IllegalArgumentException("At least one platform administrator must remain enabled");
        }
        Instant now = clock.instant();
        BackofficeAccess requested = new BackofficeAccess(iamUserId, command.enabled(), command.roleCodes(), command.dataScopes(), actualVersion + 1, now);
        BackofficeAccess saved = repository.save(requested, actualVersion, actor.iamUserId());
        audit(current == null ? "BACKOFFICE_ACCESS_CREATED" : "BACKOFFICE_ACCESS_UPDATED", iamUserId, actor,
            Map.of("enabled", Boolean.toString(saved.enabled()), "roleCount", Integer.toString(saved.roleCodes().size()),
                "scopeCount", Integer.toString(saved.dataScopes().size()), "version", Long.toString(saved.version())));
        return new AccessView(subject, saved);
    }

    private CurrentUser requireAdministrator() {
        CurrentUser actor = users.requireCurrentUser();
        if (!actor.authorities().contains(PLATFORM_ADMIN)) throw new AccessDeniedException("Platform administrator authority is required");
        return actor;
    }
    private IamUserProjection requireActiveIamUser(String iamUserId) {
        if (iamUserId == null || !IAM_ID.matcher(iamUserId).matches()) throw new IllegalArgumentException("IAM user ID is invalid");
        return iamUsers.findActiveByIamUserId(iamUserId).orElseThrow(() -> new IllegalArgumentException("IAM user is unavailable"));
    }
    private void validate(BackofficeAccessCommand command) {
        if (!ASSIGNABLE_ROLES.containsAll(command.roleCodes())) throw new IllegalArgumentException("Role code is not assignable");
        for (BackofficeDataScope scope : command.dataScopes()) {
            if (scope == null || scope.scopeType() == null || scope.scopeId() == null || !SCOPE_TYPES.contains(scope.scopeType()) || !SCOPE_ID.matcher(scope.scopeId()).matches()) {
                throw new IllegalArgumentException("Data scope is invalid");
            }
        }
    }
    private void audit(String action, String subjectIamUserId, CurrentUser actor, Map<String, String> detail) {
        audit.publish(new AuditEvent(clock.instant(), MDC.get("requestId") == null ? "system" : MDC.get("requestId"), actor.iamUserId(),
            action, "backoffice_access", subjectIamUserId, detail));
    }

    public record AccessView(IamUserProjection user, BackofficeAccess access) { }
}
