package cn.servicehub.catalog.config;

import cn.servicehub.ticket.domain.TicketType;
import java.time.Instant;
import java.util.List;

/** Immutable configuration aggregate. A published revision is never updated in place. */
public record ManagedFormConfiguration(String id, String code, String name, String summary, TicketType ticketType,
                                       String categoryCode, List<String> applicableOrganizationIds,
                                       List<ConfiguredFormField> fields, TagPolicy tagPolicy,
                                       FormConfigurationStatus status, long version, int formVersion,
                                       String schemaHash, String changeReason, String createdByIamUserId,
                                       String lastModifiedByIamUserId, Instant createdAt, Instant updatedAt,
                                       Instant publishedAt) {
    public ManagedFormConfiguration {
        applicableOrganizationIds = applicableOrganizationIds == null ? List.of() : List.copyOf(applicableOrganizationIds);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
