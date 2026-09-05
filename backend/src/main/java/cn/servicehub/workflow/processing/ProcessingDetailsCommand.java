package cn.servicehub.workflow.processing;

public record ProcessingDetailsCommand(String eventSource, String proposingOrganization,
                                       Boolean onSiteSupportRequired, String causeCategory,
                                       String processingDescription, String resolutionDescription,
                                       Boolean thirdPartyHandled, String currentProgress) {
}
