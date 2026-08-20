package cn.servicehub.iam.infrastructure;

import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.iam.domain.OrganizationSummary;
import cn.servicehub.iam.domain.PositionSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL query adapter for IAM-synchronised, read-only projection tables. */
@Repository
@Profile("mysql")
public class MySqlIamUserProjectionRepository implements IamUserProjectionRepository {
    private final JdbcTemplate jdbcTemplate;

    public MySqlIamUserProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<IamUserProjection> findActiveByIamUserId(String iamUserId) {
        List<UserProjectionRow> rows = jdbcTemplate.query("""
            SELECT u.iam_user_id, u.login_name, u.display_name, u.active AS user_active,
                   u.source_system, u.source_version, u.synced_at AS user_synced_at,
                   o.iam_organization_id, o.organization_name,
                   r.iam_position_id, r.position_name, r.is_primary
              FROM iam_user_projection u
              JOIN iam_user_organization_position_projection r ON r.iam_user_id = u.iam_user_id AND r.active = TRUE
              JOIN iam_organization_projection o ON o.iam_organization_id = r.iam_organization_id AND o.active = TRUE
             WHERE u.iam_user_id = ? AND u.active = TRUE
             ORDER BY r.is_primary DESC, o.organization_name ASC, r.position_name ASC, r.iam_position_id ASC
            """, (resultSet, rowNum) -> mapRow(resultSet), iamUserId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        UserProjectionRow primary = rows.getFirst();
        List<PositionSummary> positions = new ArrayList<>();
        for (UserProjectionRow row : rows) {
            positions.add(new PositionSummary(row.iamPositionId(), row.positionName(), row.primary()));
        }
        return Optional.of(new IamUserProjection(primary.iamUserId(), primary.loginName(), primary.displayName(), primary.active(),
            new OrganizationSummary(primary.iamOrganizationId(), primary.organizationName()), positions, primary.sourceSystem(),
            primary.sourceVersion(), primary.syncedAt()));
    }

    private UserProjectionRow mapRow(ResultSet resultSet) throws SQLException {
        return new UserProjectionRow(resultSet.getString("iam_user_id"), resultSet.getString("login_name"),
            resultSet.getString("display_name"), resultSet.getBoolean("user_active"), resultSet.getString("source_system"),
            resultSet.getString("source_version"), resultSet.getTimestamp("user_synced_at").toInstant(),
            resultSet.getString("iam_organization_id"), resultSet.getString("organization_name"),
            resultSet.getString("iam_position_id"), resultSet.getString("position_name"), resultSet.getBoolean("is_primary"));
    }

    private record UserProjectionRow(String iamUserId, String loginName, String displayName, boolean active,
                                     String sourceSystem, String sourceVersion, Instant syncedAt,
                                     String iamOrganizationId, String organizationName,
                                     String iamPositionId, String positionName, boolean primary) {
    }
}
