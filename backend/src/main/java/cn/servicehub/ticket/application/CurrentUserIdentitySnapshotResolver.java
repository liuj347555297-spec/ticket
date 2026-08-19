package cn.servicehub.ticket.application;

import cn.servicehub.security.CurrentUser;
import cn.servicehub.ticket.domain.IdentitySnapshot;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Safe interim identity resolver. The IAM projection integration replaces only this adapter;
 * browser requests cannot set any snapshot field.
 */
@Component
public class CurrentUserIdentitySnapshotResolver implements IdentitySnapshotResolver {
    private final Clock clock = Clock.systemUTC();

    @Override
    public IdentitySnapshot snapshotFor(CurrentUser user) {
        return new IdentitySnapshot(user.iamUserId(), user.iamUserId(), "未同步组织", null, clock.instant());
    }
}
