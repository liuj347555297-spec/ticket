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
    private final cn.servicehub.localauth.infrastructure.InMemoryLocalProjectionStore localProjections;
    private static final Instant SEED_SYNCED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private final Map<String, IamUserProjection> projections = Map.of(
        "iam-u-1001", new IamUserProjection("iam-u-1001", "wang.xiaoming", "王小明", true,
            new OrganizationSummary("org-it", "信息技术部"),
            java.util.List.of(new PositionSummary("pos-it-support", "IT 服务台工程师", true)), "development-seed", "seed-1", SEED_SYNCED_AT),
        "iam-u-1002", new IamUserProjection("iam-u-1002", "li.xiaohong", "李小红", true,
            new OrganizationSummary("org-finance", "财务部"),
            java.util.List.of(new PositionSummary("pos-finance", "业务专员", true)), "development-seed", "seed-1", SEED_SYNCED_AT),
        "iam-u-local-requester", local("iam-u-local-requester", "local.requester", "本地开发提单人", "POS-LOCAL-REQUESTER", "业务系统使用人"),
        "iam-u-local-first-line", local("iam-u-local-first-line", "local.firstline", "本地一线工程师", "POS-LOCAL-FIRST-LINE", "一线服务台工程师"),
        "iam-u-local-service-manager", local("iam-u-local-service-manager", "local.manager", "本地服务经理", "POS-LOCAL-MANAGER", "服务台经理"),
        "iam-u-local-admin", local("iam-u-local-admin", "local.admin", "本地开发管理员", "POS-LOCAL-ADMIN", "本地平台管理员")
    );

    public InMemoryIamUserProjectionRepository() {
        this(new cn.servicehub.localauth.infrastructure.InMemoryLocalProjectionStore());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InMemoryIamUserProjectionRepository(cn.servicehub.localauth.infrastructure.InMemoryLocalProjectionStore localProjections) {
        this.localProjections = localProjections;
    }

    @Override
    public Optional<IamUserProjection> findActiveByIamUserId(String iamUserId) {
        return localProjections.findActive(iamUserId).or(() -> Optional.ofNullable(projections.get(iamUserId)).filter(IamUserProjection::active));
    }

    private IamUserProjection local(String iamUserId, String loginName, String displayName, String positionId, String positionName) {
        return new IamUserProjection(iamUserId, loginName, displayName, true,
            new OrganizationSummary("ORG-LOCAL-IT", "本地开发 / 信息技术部"),
            java.util.List.of(new PositionSummary(positionId, positionName, true)), "LOCAL_DEV", "1", SEED_SYNCED_AT);
    }
}
