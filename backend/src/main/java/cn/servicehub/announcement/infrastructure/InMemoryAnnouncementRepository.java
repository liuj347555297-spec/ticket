package cn.servicehub.announcement.infrastructure;

import cn.servicehub.announcement.domain.Announcement;
import cn.servicehub.announcement.domain.AnnouncementRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!mysql")
public class InMemoryAnnouncementRepository implements AnnouncementRepository {
    private final ConcurrentHashMap<String, Announcement> announcements = new ConcurrentHashMap<>();
    @Override public void save(Announcement announcement) { announcements.putIfAbsent(announcement.id(), announcement); }
    @Override public List<Announcement> findActiveForAudience(String organizationId, Instant now, int limit) {
        return announcements.values().stream()
            .filter(value -> !value.effectiveFrom().isAfter(now) && value.effectiveUntil().isAfter(now))
            .filter(value -> value.audienceScope() == Announcement.AudienceScope.ALL || organizationId.equals(value.targetOrganizationIamId()))
            .sorted(Comparator.comparing(Announcement::pinned).reversed().thenComparing(Announcement::effectiveFrom).reversed())
            .limit(limit).toList();
    }
}
