-- Local browser identities are deliberately separate from IAM credentials.  The stable account
-- id is also used as the subject id in the existing identity projection/authorization model.
CREATE TABLE platform_local_account (
    id VARCHAR(128) NOT NULL,
    login_name VARCHAR(128) NOT NULL,
    normalized_login_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    iam_organization_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NOT NULL,
    session_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_local_account_normalized_login (normalized_login_name),
    KEY idx_local_account_enabled_name (enabled, display_name, id),
    KEY idx_local_account_organization (iam_organization_id, enabled),
    CONSTRAINT fk_local_account_organization FOREIGN KEY (iam_organization_id)
        REFERENCES iam_organization_projection (iam_organization_id),
    CONSTRAINT ck_local_account_failed_count CHECK (failed_login_count <= 1000000),
    CONSTRAINT ck_local_account_session_version CHECK (session_version >= 1),
    CONSTRAINT ck_local_account_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
