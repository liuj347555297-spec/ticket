-- Personal favorites are user-owned links, never global counters. Reads always join the current
-- published document and reapply organization/catalog authorization in the application service.
CREATE TABLE knowledge_document_favorite (
    iam_user_id VARCHAR(128) NOT NULL,
    document_id VARCHAR(48) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, document_id),
    KEY idx_knowledge_favorite_user_created (iam_user_id, created_at DESC),
    CONSTRAINT fk_knowledge_favorite_document FOREIGN KEY (document_id)
        REFERENCES knowledge_document (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE knowledge_document
    ADD KEY idx_knowledge_document_creator_status (creator_iam_user_id, status, updated_at),
    DROP CHECK ck_knowledge_document_status,
    ADD CONSTRAINT ck_knowledge_document_status CHECK (
        status IN ('DRAFT','IMPORTED','PENDING_REVIEW','PUBLISHED','SUPERSEDED','REJECTED','ARCHIVED','MIGRATION_PENDING')
    );

ALTER TABLE knowledge_document_version
    DROP CHECK ck_knowledge_version_status,
    ADD CONSTRAINT ck_knowledge_version_status CHECK (
        status IN ('DRAFT','IMPORTED','PENDING_REVIEW','PUBLISHED','SUPERSEDED','REJECTED','ARCHIVED','MIGRATION_PENDING')
    );
