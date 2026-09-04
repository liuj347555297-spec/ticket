-- Design metadata is isolated from all active catalog, ticket and Flowable definitions.
-- No migration deploys BPMN or changes existing form/instance history.
CREATE TABLE design_studio_draft (
    id VARCHAR(64) NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    name VARCHAR(120) NOT NULL,
    version BIGINT NOT NULL,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'DRAFT_ONLY',
    payload_json JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_design_studio_scope (organization_id, updated_at),
    CONSTRAINT ck_design_studio_version CHECK (version >= 0),
    CONSTRAINT ck_design_studio_execution CHECK (execution_mode = 'DRAFT_ONLY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
