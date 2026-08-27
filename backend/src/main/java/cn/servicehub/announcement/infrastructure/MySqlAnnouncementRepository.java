package cn.servicehub.announcement.infrastructure;

import cn.servicehub.announcement.domain.Announcement;
import cn.servicehub.announcement.domain.AnnouncementRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("mysql")
public class MySqlAnnouncementRepository implements AnnouncementRepository {
    private final JdbcTemplate jdbc;
    public MySqlAnnouncementRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void save(Announcement value) {
        jdbc.update("INSERT INTO service_announcement (id,title,body,audience_scope,target_organization_iam_id,pinned,effective_from,effective_until,creator_iam_user_id,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            value.id(), value.title(), value.body(), value.audienceScope().name(), value.targetOrganizationIamId(), value.pinned(), ts(value.effectiveFrom()), ts(value.effectiveUntil()), value.creatorIamUserId(), ts(value.createdAt()), ts(value.updatedAt()), value.version());
    }
    @Override public List<Announcement> findActiveForAudience(String organizationId, Instant now, int limit) {
        return jdbc.query("SELECT * FROM service_announcement WHERE effective_from<=? AND effective_until>? AND (audience_scope='ALL' OR (audience_scope='ORGANIZATION' AND target_organization_iam_id=?)) ORDER BY pinned DESC, effective_from DESC LIMIT ?", (rs, row) -> new Announcement(rs.getString("id"),rs.getString("title"),rs.getString("body"),Announcement.AudienceScope.valueOf(rs.getString("audience_scope")),rs.getString("target_organization_iam_id"),rs.getBoolean("pinned"),rs.getTimestamp("effective_from").toInstant(),rs.getTimestamp("effective_until").toInstant(),rs.getString("creator_iam_user_id"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getLong("version")), ts(now), ts(now), organizationId, limit);
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
