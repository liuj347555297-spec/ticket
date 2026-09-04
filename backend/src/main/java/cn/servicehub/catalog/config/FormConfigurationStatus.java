package cn.servicehub.catalog.config;

/** Lifecycle is deliberately separate from requester-facing publication status. */
public enum FormConfigurationStatus {
    DRAFT, PENDING_REVIEW, PUBLISHED, RETIRED, REJECTED
}
