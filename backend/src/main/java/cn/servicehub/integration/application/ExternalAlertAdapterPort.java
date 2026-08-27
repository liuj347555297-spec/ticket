package cn.servicehub.integration.application;

/**
 * Maps a verified, source-specific payload to the platform alert contract. Vendor client
 * implementations remain disabled until their service account and signed callback contract are approved.
 */
public interface ExternalAlertAdapterPort {
    boolean supports(String sourceCode);
    AlertInput normalize(String rawPayload);

    record AlertInput(String sourceEventId, String fingerprint, String severity, String title,
                      String configurationItemId, java.time.Instant occurredAt) { }
}
