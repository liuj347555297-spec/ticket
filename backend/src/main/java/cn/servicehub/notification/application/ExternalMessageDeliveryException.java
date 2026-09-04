package cn.servicehub.notification.application;

/** A provider-safe failure classification. Raw provider responses must never enter ticket data or logs. */
public final class ExternalMessageDeliveryException extends RuntimeException {
    private final String safeCode;
    private final boolean retryable;

    public ExternalMessageDeliveryException(String safeCode, boolean retryable) {
        super(safeCode, null, false, false);
        this.safeCode = safeCode;
        this.retryable = retryable;
    }

    public String safeCode() { return safeCode; }
    public boolean retryable() { return retryable; }
}
