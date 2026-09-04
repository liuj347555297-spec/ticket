-- V31 keeps calendar and report/export inputs immutable once a ticket or task is created.
-- Existing V1-V30 environments upgrade in place; new database baselines start from V31.
CREATE TABLE sla_work_calendar (
    calendar_key VARCHAR(64) NOT NULL,
    calendar_name VARCHAR(120) NOT NULL,
    current_version INT UNSIGNED NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (calendar_key),
    CONSTRAINT ck_sla_calendar_version_positive CHECK (current_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sla_work_calendar_version (
    calendar_key VARCHAR(64) NOT NULL,
    version INT UNSIGNED NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    all_day BOOLEAN NOT NULL,
    working_weekdays JSON NOT NULL,
    business_start TIME NULL,
    business_end TIME NULL,
    holiday_dates JSON NOT NULL,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (calendar_key, version),
    CONSTRAINT fk_sla_calendar_version_calendar FOREIGN KEY (calendar_key) REFERENCES sla_work_calendar(calendar_key),
    CONSTRAINT ck_sla_calendar_business_period CHECK ((all_day = TRUE AND business_start IS NULL AND business_end IS NULL) OR (all_day = FALSE AND business_start IS NOT NULL AND business_end IS NOT NULL AND business_end > business_start))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sla_work_calendar (calendar_key, calendar_name, current_version, active, updated_at)
VALUES ('24X7', '7×24 服务日历', 1, TRUE, UTC_TIMESTAMP(6));
INSERT INTO sla_work_calendar_version (calendar_key, version, time_zone, all_day, working_weekdays, business_start, business_end, holiday_dates, created_by_iam_user_id, created_at)
VALUES ('24X7', 1, 'UTC', TRUE, JSON_ARRAY('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'), NULL, NULL, JSON_ARRAY(), 'system-baseline', UTC_TIMESTAMP(6));

CREATE TABLE sla_escalation_event (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    target_version BIGINT NOT NULL,
    event_code VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_escalation_event_dedup (ticket_id, target_version, event_code),
    KEY idx_sla_escalation_pending (consumed_at, occurred_at),
    CONSTRAINT fk_sla_escalation_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_report_export_task (
    id CHAR(36) NOT NULL,
    requester_iam_user_id VARCHAR(128) NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    organization_scope_json JSON NOT NULL,
    unrestricted_scope BOOLEAN NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_content MEDIUMBLOB NULL,
    result_sha256 CHAR(64) NULL,
    result_file_name VARCHAR(160) NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    download_count INT UNSIGNED NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_operation_export_worker (status, created_at),
    KEY idx_operation_export_requester (requester_iam_user_id, created_at),
    CONSTRAINT ck_operation_export_dates CHECK (date_to >= date_from),
    CONSTRAINT ck_operation_export_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
