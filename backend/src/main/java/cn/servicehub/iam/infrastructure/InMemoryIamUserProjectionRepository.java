package cn.servicehub.iam.infrastructure;

import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.iam.domain.IamUserProjectionRepository;
import cn.servicehub.iam.domain.OrganizationSummary;
import cn.servicehub.iam.domain.PositionSummary;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Development/test seed only. Production is served by the MySQL read-only projection adapter. */
@Repository
@Profile("!mysql")
public class InMemoryIamUserProjectionRepository implements IamUserProjectionRepository {
    private static final Instant SEED_SYNCED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final Map<String, IamUserProjection> projections = Map.of(
        "iam-u-1001", new IamUserProjection("iam-u-1001", "wang.xiaoming", "王小明", true,
            new OrganizationSummary("org-it", "信息技术部"),
            java.util.List.of(new PositionSummary("pos-it-support", "IT 服务台工程师", true)), "development-seed", "seed-1", SEED_SYNCED_AT),
        "iam-u-1002", new IamUserProjection("iam-u-1002", "li.xiaohong", "李小红", true,
            new OrganizationSummary("org-finance", "财务部"),
            java.util.List.of(new PositionSummary("pos-finance", "业务专员", true)), "development-seed", "seed-1", SEED_SYNCED_AT)
    );

    @Override
    public Optional<IamUserProjection> findActiveByIamUserId(String iamUserId) {
        return Optional.ofNullable(projections.get(iamUserId)).filter(IamUserProjection::active);
    }
}
