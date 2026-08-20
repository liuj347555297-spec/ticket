-- IAM is the authoritative identity source. These tables are local, read-only projections used
-- for assignment, data-scope checks, message delivery and immutable business snapshots. They
-- deliberately contain no password, credential, token or local-account columns.
CREATE TABLE iam_sync_batch (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    organization_count INT UNSIGNED NOT NULL DEFAULT 0,
    user_count INT UNSIGNED NOT NULL DEFAULT 0,
    failure_summary VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_iam_sync_batch_source_started (source_system, started_at DESC),
    CONSTRAINT ck_iam_sync_batch_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'PARTIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_organization_projection (
    iam_organization_id VARCHAR(128) NOT NULL,
    organization_code VARCHAR(128) NULL,
    organization_name VARCHAR(200) NOT NULL,
    parent_iam_organization_id VARCHAR(128) NULL,
    organization_path VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_organization_id),
    KEY idx_iam_organization_parent (parent_iam_organization_id),
    KEY idx_iam_organization_code (organization_code),
    KEY idx_iam_organization_active_path (active, organization_path(255)),
    CONSTRAINT fk_iam_organization_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_projection (
    iam_user_id VARCHAR(128) NOT NULL,
    login_name VARCHAR(128) NULL,
    employee_number VARCHAR(128) NULL,
    display_name VARCHAR(100) NOT NULL,
    work_email VARCHAR(254) NULL,
    work_mobile VARCHAR(64) NULL,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id),
    KEY idx_iam_user_login_name (login_name),
    KEY idx_iam_user_employee_number (employee_number),
    KEY idx_iam_user_active_display_name (active, display_name),
    CONSTRAINT fk_iam_user_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_organization_position_projection (
    iam_user_id VARCHAR(128) NOT NULL,
    iam_organization_id VARCHAR(128) NOT NULL,
    iam_position_id VARCHAR(128) NOT NULL,
    position_name VARCHAR(200) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, iam_organization_id, iam_position_id),
    KEY idx_iam_user_org_primary (iam_user_id, active, is_primary),
    KEY idx_iam_org_user_active (iam_organization_id, active, iam_user_id),
    CONSTRAINT fk_iam_user_org_position_user FOREIGN KEY (iam_user_id) REFERENCES iam_user_projection (iam_user_id),
    CONSTRAINT fk_iam_user_org_position_organization FOREIGN KEY (iam_organization_id) REFERENCES iam_organization_projection (iam_organization_id),
    CONSTRAINT fk_iam_user_org_position_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
