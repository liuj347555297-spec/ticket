package cn.servicehub.announcement.application;

import cn.servicehub.announcement.domain.Announcement;
import cn.servicehub.announcement.domain.AnnouncementRepository;
import cn.servicehub.audit.AuditEvent;
import cn.servicehub.audit.AuditEventPublisher;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Effective visibility is calculated here from an IAM projection; it is never accepted from the browser. */
@Service
public class AnnouncementService {
    private static final Set<String> WRITE_ROLES = Set.of("ROLE_SERVICE_MANAGER", "ROLE_PLATFORM_ADMIN");
    private final AnnouncementRepository repository;
    private final IamUserProjectionRepository iamUsers;
    private final CurrentUserProvider users;
    private final AuditEventPublisher audit;
    private final Clock clock = Clock.systemUTC();
    public AnnouncementService(AnnouncementRepository repository, IamUserProjectionRepository iamUsers, CurrentUserProvider users, AuditEventPublisher audit) { this.repository = repository; this.iamUsers = iamUsers; this.users = users; this.audit = audit; }

    public List<Announcement> myActiveAnnouncements(int limit) {
        CurrentUser actor = users.requireCurrentUser();
        String organizationId = iamUsers.findActiveByIamUserId(actor.iamUserId()).orElseThrow(() -> new AccessDeniedException("Active IAM projection is required")).organization().iamOrganizationId();
        List<Announcement> values = repository.findActiveForAudience(organizationId, clock.instant(), limit);
        audit(actor, "ANNOUNCEMENT_LISTED", "collection", Map.of("returned", String.valueOf(values.size())));
        return values;
    }

    @Transactional
    public Announcement create(String title, String body, Announcement.AudienceScope scope, String targetOrganizationIamId, boolean pinned, Instant effectiveFrom, Instant effectiveUntil) {
        CurrentUser actor = users.requireCurrentUser();
        if (actor.authorities().stream().noneMatch(WRITE_ROLES::contains)) throw new AccessDeniedException("Announcement management is not authorized");
        if (title == null || title.trim().isBlank() || title.length() > 200 || body == null || body.trim().isBlank() || body.length() > 4000 || scope == null) throw new AnnouncementValidationException();
        if (scope == Announcement.AudienceScope.ORGANIZATION && (targetOrganizationIamId == null || !targetOrganizationIamId.matches("^[A-Za-z0-9:_-]{2,128}$"))) throw new AnnouncementValidationException();
        if (scope == Announcement.AudienceScope.ALL && targetOrganizationIamId != null) throw new AnnouncementValidationException();
        Instant now = clock.instant(); Instant from = effectiveFrom == null ? now : effectiveFrom; Instant until = effectiveUntil == null ? now.plus(90, ChronoUnit.DAYS) : effectiveUntil;
        if (!until.isAfter(from) || until.isAfter(now.plus(366, ChronoUnit.DAYS))) throw new AnnouncementValidationException();
        Announcement value = new Announcement("ANN-" + UUID.randomUUID(), title.trim(), body.trim(), scope, targetOrganizationIamId, pinned, from, until, actor.iamUserId(), now, now, 0);
        repository.save(value);
        audit(actor, "ANNOUNCEMENT_CREATED", value.id(), Map.of("scope", scope.name(), "pinned", String.valueOf(pinned), "effectiveUntil", until.toString()));
        return value;
    }
    private void audit(CurrentUser actor, String action, String id, Map<String, String> attributes) { String requestId = MDC.get("requestId"); audit.publish(new AuditEvent(clock.instant(), requestId == null ? "system" : requestId, actor.iamUserId(), action, "service-announcement", id, attributes)); }
}
