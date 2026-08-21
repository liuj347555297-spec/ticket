package cn.servicehub.attachment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "servicehub.attachment")
public record AttachmentProperties(String storageRoot, long maxFileSizeBytes, int maxFilesPerTicket) {
    public AttachmentProperties {
        if (storageRoot == null || storageRoot.isBlank()) throw new IllegalArgumentException("Attachment storage root is required");
        if (maxFileSizeBytes < 1 || maxFilesPerTicket < 1) throw new IllegalArgumentException("Attachment limits must be positive");
    }
}
