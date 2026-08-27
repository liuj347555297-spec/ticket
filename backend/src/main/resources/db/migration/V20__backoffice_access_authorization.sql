-- IAM remains the authority for identities and organizations.  This table family contains only
-- platform-local backoffice entitlements bound to an already synchronised IAM identity.
CREATE TABLE platform_backoffice_user (
    iam_user_id VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id),
    CONSTRAINT fk_backoffice_user_iam FOREIGN KEY (iam_user_id) REFERENCES iam_user_projection (iam_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_backoffice_user_role (
    iam_user_id VARCHAR(128) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL,
    granted_by_iam_user_id VARCHAR(128) NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_by_iam_user_id VARCHAR(128) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (iam_user_id, role_code),
    KEY idx_backoffice_role_active (role_code, active, iam_user_id),
    CONSTRAINT fk_backoffice_role_user FOREIGN KEY (iam_user_id) REFERENCES platform_backoffice_user (iam_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_backoffice_user_data_scope (
    iam_user_id VARCHAR(128) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL,
    granted_by_iam_user_id VARCHAR(128) NOT NULL,
    granted_at DATETIME(6) NOT NULL,
    revoked_by_iam_user_id VARCHAR(128) NULL,
    revoked_at DATETIME(6) NULL,
    PRIMARY KEY (iam_user_id, scope_type, scope_id),
    KEY idx_backoffice_scope_active (iam_user_id, active),
    CONSTRAINT fk_backoffice_scope_user FOREIGN KEY (iam_user_id) REFERENCES platform_backoffice_user (iam_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
