CREATE TABLE personal_ticket_draft (
    id VARCHAR(40) NOT NULL,
    owner_id VARCHAR(128) NOT NULL,
    title VARCHAR(200) NOT NULL,
    payload_json JSON NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_personal_draft_owner_updated (owner_id, updated_at, id),
    CONSTRAINT ck_personal_draft_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
