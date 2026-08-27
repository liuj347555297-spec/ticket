-- Platform roles are synchronised IAM projections. They are query-only evidence for
-- assignment and approval candidate resolution; neither the browser nor this application
-- can grant a role by writing this table.
CREATE TABLE iam_user_role_projection (
    iam_user_id VARCHAR(128) NOT NULL,
    role_code VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, role_code),
    KEY idx_iam_user_role_active (role_code, active, iam_user_id),
    CONSTRAINT fk_iam_user_role_user FOREIGN KEY (iam_user_id) REFERENCES iam_user_projection (iam_user_id),
    CONSTRAINT fk_iam_user_role_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
