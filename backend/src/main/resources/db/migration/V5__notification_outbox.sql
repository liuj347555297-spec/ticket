-- Notifications are a local delivery projection.  Recipient identities are IAM user IDs only;
-- no local account, password, external token or browser-supplied recipient is persisted here.
CREATE TABLE notification_channel_route_rule (
    id VARCHAR(64) NOT NULL,
    iam_organization_id VARCHAR(128) NOT NULL,
    include_descendants BOOLEAN NOT NULL DEFAULT FALSE,
    event_type VARCHAR(64) NOT NULL DEFAULT '*',
    preferred_channel VARCHAR(32) NOT NULL,
    provider_channel_code VARCHAR(128) NULL,
    fallback_channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_route_match (iam_organization_id, enabled, priority),
    CONSTRAINT ck_notification_route_priority CHECK (priority >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed rows demonstrate organisation-specific WPS channel mappings. WPS delivery remains
-- disabled; these values are routing aliases, not endpoint addresses, recipients or credentials.
INSERT INTO notification_channel_route_rule (id, iam_organization_id, include_descendants, event_type, preferred_channel, provider_channel_code, fallback_channel, priority, enabled, version, created_at, updated_at)
VALUES ('ROUTE-IT-WPS', 'org-it', FALSE, '*', 'WPS_IM', 'wps-it-service-desk', 'IN_APP', 10, TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('ROUTE-FINANCE-WPS', 'org-finance', FALSE, '*', 'WPS_IM', 'wps-finance-service-desk', 'IN_APP', 10, TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

CREATE TABLE notification (
    id VARCHAR(80) NOT NULL,
    recipient_iam_user_id VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    ticket_id VARCHAR(24) NULL,
    payload JSON NOT NULL,
    routing_snapshot JSON NOT NULL,
    deduplication_key VARCHAR(128) NOT NULL,
    read_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient_unread (recipient_iam_user_id, read_at, created_at DESC),
    KEY idx_notification_ticket (ticket_id, created_at DESC),
    UNIQUE KEY uk_notification_deduplication (deduplication_key),
    CONSTRAINT fk_notification_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_delivery (
    id VARCHAR(80) NOT NULL,
    notification_id VARCHAR(80) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider_channel_code VARCHAR(128) NULL,
    route_rule_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    provider_message_id VARCHAR(256) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_channel (notification_id, channel),
    KEY idx_notification_delivery_retry (status, next_attempt_at),
    CONSTRAINT ck_notification_delivery_attempts_nonnegative CHECK (attempt_count >= 0),
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES notification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The dispatcher is deliberately decoupled from the request transaction.  A worker may claim
-- PENDING rows using a lease in a later release; browser requests must never call IM providers.
CREATE TABLE message_outbox (
    id VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_message_outbox_dispatch (status, available_at, created_at),
    CONSTRAINT ck_message_outbox_attempts_nonnegative CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preference (
    iam_user_id VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, channel, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
