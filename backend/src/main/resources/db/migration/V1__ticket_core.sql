-- MySQL 8.0 baseline. All business timestamps are stored in UTC by the application.
CREATE TABLE ticket_number_sequence (
    business_date DATE NOT NULL,
    sequence_value INT UNSIGNED NOT NULL,
    PRIMARY KEY (business_date),
    CONSTRAINT ck_ticket_number_sequence_positive CHECK (sequence_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket (
    id VARCHAR(24) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(8) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    structured_fields JSON NOT NULL,
    tags JSON NOT NULL,
    related_configuration_item_ids JSON NOT NULL,
    requester_iam_user_id VARCHAR(128) NOT NULL,
    requester_display_name VARCHAR(100) NOT NULL,
    requester_organization_name VARCHAR(200) NOT NULL,
    requester_position_name VARCHAR(200) NULL,
    requester_captured_at DATETIME(6) NOT NULL,
    service_catalog_item_id VARCHAR(64) NOT NULL,
    service_catalog_item_name VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ticket_requester_created (requester_iam_user_id, created_at DESC),
    KEY idx_ticket_status_created (status, created_at DESC),
    KEY idx_ticket_type_created (type, created_at DESC),
    KEY idx_ticket_catalog_created (service_catalog_item_id, created_at DESC),
    CONSTRAINT ck_ticket_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_idempotency (
    actor_iam_user_id VARCHAR(128) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_iam_user_id, idempotency_key),
    UNIQUE KEY uk_ticket_idempotency_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Designed for the next audit/outbox implementation. No event body is accepted from browsers.
CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    attributes JSON NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_event_resource (resource_type, resource_id, occurred_at DESC),
    KEY idx_audit_event_actor (actor_iam_user_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
