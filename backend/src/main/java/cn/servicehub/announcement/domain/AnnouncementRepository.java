package cn.servicehub.announcement.domain;

import java.time.Instant;
import java.util.List;

public interface AnnouncementRepository {
    void save(Announcement announcement);
    List<Announcement> findActiveForAudience(String iamOrganizationId, Instant now, int limit);
}
