package cn.servicehub.ticket.application;

import cn.servicehub.iam.application.IamProjectionService;
import cn.servicehub.iam.domain.IamUserProjection;
import cn.servicehub.security.CurrentUser;
import cn.servicehub.ticket.domain.IdentitySnapshot;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Takes the submitter snapshot from the server-side IAM projection. Browser requests cannot set
 * any snapshot field, and later IAM changes cannot rewrite an already-created ticket snapshot.
 */
@Component
public class CurrentUserIdentitySnapshotResolver implements IdentitySnapshotResolver {
    private final IamProjectionService iamProjectionService;
    private final Clock clock = Clock.systemUTC();

    public CurrentUserIdentitySnapshotResolver(IamProjectionService iamProjectionService) {
        this.iamProjectionService = iamProjectionService;
    }

    @Override
    public IdentitySnapshot snapshotFor(CurrentUser user) {
        IamUserProjection projection = iamProjectionService.requireActiveProjection(user.iamUserId());
        String primaryPosition = projection.positions().stream().filter(position -> position.primary())
            .findFirst().map(position -> position.name()).orElse(null);
        return new IdentitySnapshot(projection.iamUserId(), projection.displayName(), projection.organization().iamOrganizationId(), projection.organization().name(),
            primaryPosition, clock.instant());
    }
}
