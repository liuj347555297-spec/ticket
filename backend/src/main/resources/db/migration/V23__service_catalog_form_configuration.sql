-- Back-office form configuration. The legacy requester-facing catalog remains read-only until a published
-- configuration is explicitly bound by the application. Definitions are immutable after publication.
CREATE TABLE service_catalog_form_configuration (
    id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    summary VARCHAR(500) NULL,
    ticket_type VARCHAR(32) NOT NULL,
    category_code VARCHAR(64) NOT NULL,
    applicable_organization_ids JSON NOT NULL,
    fields_json JSON NOT NULL,
    tag_policy_json JSON NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    form_version INT NOT NULL,
    schema_hash CHAR(64) NOT NULL,
    change_reason VARCHAR(500) NOT NULL,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    last_modified_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_catalog_form_configuration_code (code),
    KEY idx_service_catalog_form_configuration_status (lifecycle_status, updated_at),
    CONSTRAINT ck_service_catalog_form_configuration_status CHECK (lifecycle_status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','RETIRED','REJECTED')),
    CONSTRAINT ck_service_catalog_form_configuration_version CHECK (version >= 0),
    CONSTRAINT ck_service_catalog_form_configuration_form_version CHECK (form_version >= 1),
    CONSTRAINT ck_service_catalog_form_configuration_ticket_type CHECK (ticket_type IN ('INCIDENT','SERVICE_REQUEST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_form_publication_request (
    id VARCHAR(64) NOT NULL,
    catalog_item_id VARCHAR(64) NOT NULL,
    requested_version BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    applicant_iam_user_id VARCHAR(128) NOT NULL,
    lifecycle_status VARCHAR(32) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    decided_by_iam_user_id VARCHAR(128) NULL,
    decided_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_service_catalog_form_publication_request_item (catalog_item_id, lifecycle_status, requested_at),
    CONSTRAINT fk_service_catalog_form_publication_request_item FOREIGN KEY (catalog_item_id) REFERENCES service_catalog_form_configuration(id),
    CONSTRAINT ck_service_catalog_form_publication_request_status CHECK (lifecycle_status IN ('PENDING_REVIEW','PUBLISHED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
