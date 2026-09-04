-- Knowledge without a trustworthy organization/catalog scope must never remain business-visible.
ALTER TABLE knowledge_document
    ADD COLUMN owning_organization_id VARCHAR(128) NULL AFTER category_code,
    ADD COLUMN service_catalog_item_ids JSON NULL AFTER owning_organization_id,
    ADD KEY idx_knowledge_document_scope (owning_organization_id, status, updated_at),
    DROP CHECK ck_knowledge_document_status,
    ADD CONSTRAINT ck_knowledge_document_status CHECK (status IN ('IMPORTED','PENDING_REVIEW','PUBLISHED','REJECTED','ARCHIVED','MIGRATION_PENDING'));

ALTER TABLE knowledge_document_version
    ADD COLUMN owning_organization_id VARCHAR(128) NULL AFTER size_bytes,
    ADD COLUMN service_catalog_item_ids JSON NULL AFTER owning_organization_id,
    ADD KEY idx_knowledge_version_scope (owning_organization_id, status, created_at),
    DROP CHECK ck_knowledge_version_status,
    ADD CONSTRAINT ck_knowledge_version_status CHECK (status IN ('IMPORTED','PENDING_REVIEW','PUBLISHED','REJECTED','ARCHIVED','MIGRATION_PENDING'));

-- No default/global owner is inferred for legacy rows. They stay audit-only until an explicit reviewed migration assigns both fields.
UPDATE knowledge_document SET status = 'MIGRATION_PENDING'
WHERE owning_organization_id IS NULL OR service_catalog_item_ids IS NULL OR JSON_LENGTH(service_catalog_item_ids) = 0;

UPDATE knowledge_document_version SET status = 'MIGRATION_PENDING'
WHERE owning_organization_id IS NULL OR service_catalog_item_ids IS NULL OR JSON_LENGTH(service_catalog_item_ids) = 0;

ALTER TABLE knowledge_document
    ADD CONSTRAINT ck_knowledge_document_scope_complete CHECK (
        status = 'MIGRATION_PENDING' OR
        (owning_organization_id IS NOT NULL AND owning_organization_id <> '' AND JSON_TYPE(service_catalog_item_ids) = 'ARRAY' AND JSON_LENGTH(service_catalog_item_ids) > 0)
    );

ALTER TABLE knowledge_document_version
    ADD CONSTRAINT ck_knowledge_version_scope_complete CHECK (
        status = 'MIGRATION_PENDING' OR
        (owning_organization_id IS NOT NULL AND owning_organization_id <> '' AND JSON_TYPE(service_catalog_item_ids) = 'ARRAY' AND JSON_LENGTH(service_catalog_item_ids) > 0)
    );
