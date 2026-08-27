package cn.servicehub.access.infrastructure;

import cn.servicehub.access.application.BackofficeAccessConflictException;
import cn.servicehub.access.domain.BackofficeAccess;
import cn.servicehub.access.domain.BackofficeAccessRepository;
import cn.servicehub.access.domain.BackofficeDataScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL persistence for platform-local backoffice authorization; it never writes IAM projection tables. */
@Repository
@Profile("mysql")
public class MySqlBackofficeAccessRepository implements BackofficeAccessRepository {
    private final JdbcTemplate jdbc;
    public MySqlBackofficeAccessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Optional<BackofficeAccess> findByIamUserId(String iamUserId) {
        List<BackofficeAccess> rows = jdbc.query("SELECT iam_user_id, enabled, version, updated_at FROM platform_backoffice_user WHERE iam_user_id=?",
            (rs, row) -> new BackofficeAccess(rs.getString("iam_user_id"), rs.getBoolean("enabled"), roles(rs.getString("iam_user_id")), scopes(rs.getString("iam_user_id")), rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()), iamUserId);
        return rows.stream().findFirst();
    }
    @Override public List<String> findEnabledIamUserIdsByRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) return List.of();
        String placeholders = String.join(",", roleCodes.stream().map(value -> "?").toList());
        return jdbc.queryForList("SELECT DISTINCT r.iam_user_id FROM platform_backoffice_user_role r JOIN platform_backoffice_user u ON u.iam_user_id=r.iam_user_id AND u.enabled=TRUE JOIN iam_user_projection i ON i.iam_user_id=u.iam_user_id AND i.active=TRUE WHERE r.active=TRUE AND r.role_code IN (" + placeholders + ") ORDER BY r.iam_user_id", String.class, roleCodes.toArray());
    }
    @Override public long countEnabledUsersWithRole(String roleCode) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM platform_backoffice_user_role r JOIN platform_backoffice_user u ON u.iam_user_id=r.iam_user_id AND u.enabled=TRUE JOIN iam_user_projection i ON i.iam_user_id=u.iam_user_id AND i.active=TRUE WHERE r.active=TRUE AND r.role_code=?", Long.class, roleCode);
        return count == null ? 0 : count;
    }
    @Override public BackofficeAccess save(BackofficeAccess access, long expectedVersion, String actorIamUserId) {
        Timestamp now = Timestamp.from(access.updatedAt());
        if (expectedVersion == 0) {
            if (jdbc.update("INSERT INTO platform_backoffice_user (iam_user_id, enabled, version, created_by_iam_user_id, updated_by_iam_user_id, created_at, updated_at) SELECT ?, ?, ?, ?, ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM platform_backoffice_user WHERE iam_user_id=?)", access.iamUserId(), access.enabled(), access.version(), actorIamUserId, actorIamUserId, now, now, access.iamUserId()) != 1) throw new BackofficeAccessConflictException();
        } else if (jdbc.update("UPDATE platform_backoffice_user SET enabled=?, version=?, updated_by_iam_user_id=?, updated_at=? WHERE iam_user_id=? AND version=?", access.enabled(), access.version(), actorIamUserId, now, access.iamUserId(), expectedVersion) != 1) {
            throw new BackofficeAccessConflictException();
        }
        jdbc.update("UPDATE platform_backoffice_user_role SET active=FALSE, revoked_by_iam_user_id=?, revoked_at=? WHERE iam_user_id=? AND active=TRUE", actorIamUserId, now, access.iamUserId());
        for (String role : access.roleCodes()) jdbc.update("INSERT INTO platform_backoffice_user_role (iam_user_id, role_code, active, granted_by_iam_user_id, granted_at, revoked_by_iam_user_id, revoked_at) VALUES (?, ?, TRUE, ?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE active=TRUE, granted_by_iam_user_id=VALUES(granted_by_iam_user_id), granted_at=VALUES(granted_at), revoked_by_iam_user_id=NULL, revoked_at=NULL", access.iamUserId(), role, actorIamUserId, now);
        jdbc.update("UPDATE platform_backoffice_user_data_scope SET active=FALSE, revoked_by_iam_user_id=?, revoked_at=? WHERE iam_user_id=? AND active=TRUE", actorIamUserId, now, access.iamUserId());
        for (BackofficeDataScope scope : access.dataScopes()) jdbc.update("INSERT INTO platform_backoffice_user_data_scope (iam_user_id, scope_type, scope_id, active, granted_by_iam_user_id, granted_at, revoked_by_iam_user_id, revoked_at) VALUES (?, ?, ?, TRUE, ?, ?, NULL, NULL) ON DUPLICATE KEY UPDATE active=TRUE, granted_by_iam_user_id=VALUES(granted_by_iam_user_id), granted_at=VALUES(granted_at), revoked_by_iam_user_id=NULL, revoked_at=NULL", access.iamUserId(), scope.scopeType(), scope.scopeId(), actorIamUserId, now);
        return access;
    }
    private Set<String> roles(String iamUserId) { return Set.copyOf(new LinkedHashSet<>(jdbc.queryForList("SELECT role_code FROM platform_backoffice_user_role WHERE iam_user_id=? AND active=TRUE ORDER BY role_code", String.class, iamUserId))); }
    private Set<BackofficeDataScope> scopes(String iamUserId) { return Set.copyOf(new LinkedHashSet<>(jdbc.query("SELECT scope_type, scope_id FROM platform_backoffice_user_data_scope WHERE iam_user_id=? AND active=TRUE ORDER BY scope_type, scope_id", (rs, row) -> new BackofficeDataScope(rs.getString("scope_type"), rs.getString("scope_id")), iamUserId))); }
}
