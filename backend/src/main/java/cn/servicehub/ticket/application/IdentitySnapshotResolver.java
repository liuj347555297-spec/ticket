package cn.servicehub.ticket.application;

import cn.servicehub.security.CurrentUser;
import cn.servicehub.ticket.domain.IdentitySnapshot;

/** Adapter seam for the read-only IAM user/organization projection. */
public interface IdentitySnapshotResolver {
    IdentitySnapshot snapshotFor(CurrentUser user);
}
