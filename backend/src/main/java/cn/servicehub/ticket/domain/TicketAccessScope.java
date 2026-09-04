package cn.servicehub.ticket.domain;

import java.util.Set;

/**
 * One authorization package: values of the same type are a union, while populated different
 * types are an intersection. Queue scope is deliberately closed until durable queue snapshots exist.
 */
public record TicketAccessScope(String actorIamUserId, Set<String> roleCodes,
                                Set<String> organizationIds, Set<String> serviceCatalogItemIds,
                                Set<String> serviceSystemCodes, Set<String> configurationItemIds,
                                boolean scopedTicketRole, boolean failClosed, boolean legacyDirectBypass) {
    public TicketAccessScope {
        roleCodes = copy(roleCodes); organizationIds = copy(organizationIds);
        serviceCatalogItemIds = copy(serviceCatalogItemIds); serviceSystemCodes = copy(serviceSystemCodes);
        configurationItemIds = copy(configurationItemIds);
    }

    public boolean hasAnyScope() {
        return !(organizationIds.isEmpty() && serviceCatalogItemIds.isEmpty()
            && serviceSystemCodes.isEmpty() && configurationItemIds.isEmpty());
    }

    public boolean allowsScoped(TicketObjectContext context) {
        if (!scopedTicketRole || failClosed) return false;
        if (legacyDirectBypass) return true;
        if (!hasAnyScope()) return false;
        return (organizationIds.isEmpty() || organizationIds.contains(context.requesterOrganizationId()))
            && (serviceCatalogItemIds.isEmpty() || serviceCatalogItemIds.contains(context.serviceCatalogItemId()))
            && (serviceSystemCodes.isEmpty() || serviceSystemCodes.contains(context.serviceSystemCode()))
            && (configurationItemIds.isEmpty() || context.configurationItemIds().stream().anyMatch(configurationItemIds::contains));
    }

    private static Set<String> copy(Set<String> values) { return values == null ? Set.of() : Set.copyOf(values); }
}
