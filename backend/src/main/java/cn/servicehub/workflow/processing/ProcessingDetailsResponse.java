package cn.servicehub.workflow.processing;

import java.time.Instant;

public record ProcessingDetailsResponse(String ticketId, String eventSource, String proposingOrganization,
                                        Boolean onSiteSupportRequired, String causeCategory,
                                        String processingDescription, String resolutionDescription,
                                        Boolean thirdPartyHandled, String currentProgress, long version,
                                        String updatedByIamUserId, Instant updatedAt, boolean editable) {
    static ProcessingDetailsResponse from(ProcessingDetails value, boolean editable) {
        return new ProcessingDetailsResponse(value.ticketId(), value.eventSource(), value.proposingOrganization(),
            value.onSiteSupportRequired(), value.causeCategory(), value.processingDescription(), value.resolutionDescription(),
            value.thirdPartyHandled(), value.currentProgress(), value.version(), value.updatedByIamUserId(), value.updatedAt(), editable);
    }

    static ProcessingDetailsResponse empty(String ticketId, boolean editable) {
        return new ProcessingDetailsResponse(ticketId, null, null, null, null, null, null, null, null, 0, null, null, editable);
    }
}
