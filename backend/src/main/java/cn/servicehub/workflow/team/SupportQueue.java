package cn.servicehub.workflow.team;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record SupportQueue(String code, String name, String owningOrganizationId,
                           Set<String> serviceCatalogItemIds, Set<SupportQueueScope> scopes,
                           List<SupportQueueMember> members, boolean sharedClaimEnabled,
                           Integer capacityLimit, Instant effectiveFrom, Instant effectiveUntil,
                           SupportQueueStatus status, long version) {
    public SupportQueue {
        serviceCatalogItemIds = serviceCatalogItemIds == null ? Set.of() : Set.copyOf(serviceCatalogItemIds);
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
        members = members == null ? List.of() : List.copyOf(members);
    }
    public boolean activeAt(Instant at) { return status == SupportQueueStatus.ACTIVE && !effectiveFrom.isAfter(at) && (effectiveUntil == null || effectiveUntil.isAfter(at)); }
}
