-- Attachment bytes are never stored in the database or exposed by a direct object URL. storage_key is a
-- server-generated opaque key for the configured storage adapter; client filenames are metadata only.
CREATE TABLE ticket_attachment (
    id VARCHAR(48) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    original_filename VARCHAR(128) NOT NULL,
    storage_key VARCHAR(192) NOT NULL,
    detected_media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    scan_status VARCHAR(32) NOT NULL,
    scan_detail VARCHAR(100) NULL,
    uploader_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_attachment_storage_key (storage_key),
    KEY idx_ticket_attachment_ticket (ticket_id, created_at),
    KEY idx_ticket_attachment_scan (scan_status, created_at),
    CONSTRAINT fk_ticket_attachment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT ck_ticket_attachment_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_ticket_attachment_scan_status CHECK (scan_status IN ('QUARANTINED', 'CLEAN', 'REJECTED', 'SCAN_FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Knowledge is imported as versioned, reviewed metadata. It intentionally has no full-text/vector index,
-- AI prompt, generated answer, or automatic publication path.
CREATE TABLE knowledge_document (
    id VARCHAR(48) NOT NULL,
    title VARCHAR(200) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    tags JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id VARCHAR(48) NOT NULL,
    creator_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_document_status (status, updated_at),
    CONSTRAINT ck_knowledge_document_status CHECK (status IN ('IMPORTED', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_document_version (
    id VARCHAR(48) NOT NULL,
    document_id VARCHAR(48) NOT NULL,
    version_number INT NOT NULL,
    attachment_storage_key VARCHAR(192) NOT NULL,
    detected_media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reviewer_iam_user_id VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_knowledge_version_number (document_id, version_number),
    UNIQUE KEY uk_knowledge_version_storage_key (attachment_storage_key),
    KEY idx_knowledge_version_review (status, created_at),
    CONSTRAINT fk_knowledge_version_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id),
    CONSTRAINT ck_knowledge_version_size_positive CHECK (size_bytes > 0),
    CONSTRAINT ck_knowledge_version_status CHECK (status IN ('IMPORTED', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_import_task (
    id VARCHAR(48) NOT NULL,
    document_id VARCHAR(48) NOT NULL,
    source_attachment_id VARCHAR(48) NULL,
    status VARCHAR(32) NOT NULL,
    submitter_iam_user_id VARCHAR(128) NOT NULL,
    error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_import_task_status (status, created_at),
    CONSTRAINT fk_knowledge_import_document FOREIGN KEY (document_id) REFERENCES knowledge_document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
