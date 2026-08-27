-- Announcements are served only from a server-calculated IAM audience and always retain creator/effective-window metadata for audit.
CREATE TABLE service_announcement (
    id VARCHAR(48) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    audience_scope VARCHAR(32) NOT NULL,
    target_organization_iam_id VARCHAR(128) NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    effective_from DATETIME(6) NOT NULL,
    effective_until DATETIME(6) NOT NULL,
    creator_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_service_announcement_effective (effective_from, effective_until, pinned),
    KEY idx_service_announcement_org (audience_scope, target_organization_iam_id, effective_until),
    CONSTRAINT ck_service_announcement_scope CHECK (audience_scope IN ('ALL', 'ORGANIZATION')),
    CONSTRAINT ck_service_announcement_window CHECK (effective_until > effective_from),
    CONSTRAINT ck_service_announcement_target CHECK ((audience_scope = 'ALL' AND target_organization_iam_id IS NULL) OR (audience_scope = 'ORGANIZATION' AND target_organization_iam_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
