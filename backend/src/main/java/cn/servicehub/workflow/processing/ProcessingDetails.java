package cn.servicehub.workflow.processing;

import java.time.Instant;

/** Persisted operator-entered facts for one ticket; lifecycle state remains in the workflow aggregate. */
public record ProcessingDetails(String ticketId, String eventSource, String proposingOrganization,
                                Boolean onSiteSupportRequired, String causeCategory,
                                String processingDescription, String resolutionDescription,
                                Boolean thirdPartyHandled, String currentProgress, long version,
                                String updatedByIamUserId, Instant updatedAt) {
}
