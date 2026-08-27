-- V8 deliberately stores only connection metadata and CMDB read-only projections.  Secrets are
-- opaque vault/KMS references; raw alert payloads, tokens and external endpoint credentials are
-- never persisted by the service desk.
CREATE TABLE external_connection_configuration (
    connection_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    system_type VARCHAR(32) NOT NULL,
    trusted_base_url VARCHAR(512) NOT NULL,
    secret_ref VARCHAR(256) NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms INT UNSIGNED NOT NULL,
    rate_limit_per_minute INT UNSIGNED NOT NULL,
    allowed_callback_source_ips JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (connection_code),
    CONSTRAINT ck_external_connection_type CHECK (system_type IN ('CMDB', 'MONITORING', 'LOG_PLATFORM', 'APM')),
    CONSTRAINT ck_external_connection_timeout CHECK (timeout_ms BETWEEN 100 AND 30000),
    CONSTRAINT ck_external_connection_rate CHECK (rate_limit_per_minute BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cmdb_configuration_item_projection (
    ci_id VARCHAR(128) NOT NULL,
    source_code VARCHAR(40) NOT NULL,
    ci_name VARCHAR(240) NOT NULL,
    ci_type VARCHAR(80) NOT NULL,
    ci_status VARCHAR(64) NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    source_version VARCHAR(128) NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ci_id),
    KEY idx_cmdb_ci_organization (organization_id, active, ci_name),
    KEY idx_cmdb_ci_source (source_code, active),
    CONSTRAINT fk_cmdb_ci_connection FOREIGN KEY (source_code) REFERENCES external_connection_configuration(connection_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_configuration_item (
    ticket_id VARCHAR(24) NOT NULL,
    ci_id VARCHAR(128) NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, ci_id),
    KEY idx_ticket_ci_ci (ci_id, ticket_id),
    CONSTRAINT fk_ticket_ci_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_ci_projection FOREIGN KEY (ci_id) REFERENCES cmdb_configuration_item_projection(ci_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE external_normalized_alert (
    id VARCHAR(48) NOT NULL,
    source_code VARCHAR(40) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(240) NOT NULL,
    ci_id VARCHAR(128) NULL,
    alert_status VARCHAR(32) NOT NULL,
    idempotency_status VARCHAR(24) NOT NULL,
    ticket_id VARCHAR(24) NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_alert_source_event (source_code, source_event_id),
    KEY idx_external_alert_recent (received_at),
    KEY idx_external_alert_ci (ci_id, occurred_at),
    KEY idx_external_alert_ticket (ticket_id, occurred_at),
    CONSTRAINT fk_external_alert_source FOREIGN KEY (source_code) REFERENCES external_connection_configuration(connection_code),
    CONSTRAINT fk_external_alert_ci FOREIGN KEY (ci_id) REFERENCES cmdb_configuration_item_projection(ci_id),
    CONSTRAINT fk_external_alert_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT ck_external_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
