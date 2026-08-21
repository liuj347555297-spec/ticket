-- SLA is an application-owned projection. Flowable and notification tables are deliberately not
-- used as reporting sources: daily summaries keep dashboard reads bounded.
ALTER TABLE ticket ADD COLUMN requester_organization_id VARCHAR(128) NULL AFTER requester_display_name;
UPDATE ticket SET requester_organization_id = '' WHERE requester_organization_id IS NULL;
ALTER TABLE ticket MODIFY requester_organization_id VARCHAR(128) NOT NULL;

CREATE TABLE sla_policy (
    id CHAR(36) NOT NULL,
    policy_name VARCHAR(120) NOT NULL,
    service_catalog_item_id VARCHAR(64) NULL,
    priority VARCHAR(8) NULL,
    organization_scope_id VARCHAR(128) NULL,
    response_target_minutes INT UNSIGNED NOT NULL,
    resolution_target_minutes INT UNSIGNED NOT NULL,
    calendar_key VARCHAR(64) NOT NULL,
    pause_statuses JSON NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_policy_name (policy_name),
    KEY idx_sla_policy_match (active, service_catalog_item_id, priority, organization_scope_id),
    CONSTRAINT ck_sla_response_target_positive CHECK (response_target_minutes > 0),
    CONSTRAINT ck_sla_resolution_target_positive CHECK (resolution_target_minutes > 0),
    CONSTRAINT ck_sla_policy_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sla_policy (id, policy_name, service_catalog_item_id, priority, organization_scope_id,
                        response_target_minutes, resolution_target_minutes, calendar_key, pause_statuses, active, version, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000007', '默认 P3 服务台', NULL, 'P3', NULL, 60, 480, '24X7', JSON_ARRAY('ON_HOLD', 'PENDING_USER_FEEDBACK'), TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

CREATE TABLE ticket_sla_target (
    ticket_id VARCHAR(24) NOT NULL,
    policy_id CHAR(36) NOT NULL,
    policy_name_snapshot VARCHAR(120) NOT NULL,
    calendar_key_snapshot VARCHAR(64) NOT NULL,
    response_due_at DATETIME(6) NOT NULL,
    resolution_due_at DATETIME(6) NOT NULL,
    first_responded_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    paused_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    pause_started_at DATETIME(6) NULL,
    risk_level VARCHAR(16) NOT NULL,
    response_breached BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_breached BOOLEAN NOT NULL DEFAULT FALSE,
    calculated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (ticket_id),
    KEY idx_ticket_sla_risk (risk_level, resolution_due_at),
    KEY idx_ticket_sla_breach (resolution_breached, resolution_due_at),
    CONSTRAINT fk_ticket_sla_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_sla_policy FOREIGN KEY (policy_id) REFERENCES sla_policy(id),
    CONSTRAINT ck_ticket_sla_paused_seconds_nonnegative CHECK (paused_seconds >= 0),
    CONSTRAINT ck_ticket_sla_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_report_refresh_job (
    job_key VARCHAR(64) NOT NULL,
    last_started_at DATETIME(6) NULL,
    last_succeeded_at DATETIME(6) NULL,
    last_status VARCHAR(16) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    refreshed_from DATE NULL,
    refreshed_to DATE NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO operation_report_refresh_job (job_key, last_status, updated_at)
VALUES ('daily-ticket-kpi', 'NEVER', UTC_TIMESTAMP(6));

CREATE TABLE operation_daily_ticket_kpi (
    business_date DATE NOT NULL,
    requester_organization_id VARCHAR(128) NOT NULL,
    ticket_status VARCHAR(32) NOT NULL,
    ticket_volume INT UNSIGNED NOT NULL,
    open_volume INT UNSIGNED NOT NULL,
    response_seconds_sum BIGINT UNSIGNED NOT NULL DEFAULT 0,
    response_sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    resolution_seconds_sum BIGINT UNSIGNED NOT NULL DEFAULT 0,
    resolution_sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    at_risk_volume INT UNSIGNED NOT NULL DEFAULT 0,
    breached_volume INT UNSIGNED NOT NULL DEFAULT 0,
    refreshed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (business_date, requester_organization_id, ticket_status),
    KEY idx_operation_daily_kpi_date_org (business_date, requester_organization_id),
    CONSTRAINT ck_operation_daily_ticket_volume CHECK (ticket_volume >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
