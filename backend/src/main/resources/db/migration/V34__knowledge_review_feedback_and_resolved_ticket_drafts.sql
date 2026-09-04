-- Knowledge operations remain metadata-driven.  Source tickets are only referenced by ID: their
-- narratives, comments and attachments are never denormalized into the knowledge domain.
ALTER TABLE knowledge_document
    ADD COLUMN review_due_at DATETIME(6) NULL,
    ADD COLUMN review_owner_iam_user_id VARCHAR(128) NULL,
    ADD COLUMN source_ticket_id VARCHAR(24) NULL,
    ADD KEY idx_knowledge_document_review_due (status, review_due_at),
    ADD KEY idx_knowledge_document_source_ticket (source_ticket_id);

CREATE TABLE knowledge_feedback (
    document_id VARCHAR(48) NOT NULL,
    version_id VARCHAR(48) NOT NULL,
    voter_iam_user_id VARCHAR(128) NOT NULL,
    feedback_value VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (document_id, version_id, voter_iam_user_id),
    KEY idx_knowledge_feedback_version (version_id, feedback_value),
    CONSTRAINT fk_knowledge_feedback_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
    CONSTRAINT fk_knowledge_feedback_version FOREIGN KEY (version_id) REFERENCES knowledge_document_version(id),
    CONSTRAINT ck_knowledge_feedback_value CHECK (feedback_value IN ('HELPFUL','NOT_HELPFUL')),
    CONSTRAINT ck_knowledge_feedback_reason CHECK (reason_code IS NULL OR reason_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
