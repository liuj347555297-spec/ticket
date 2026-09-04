-- ServiceHub MySQL schema baseline (v28)
-- Generated from Flyway V1-V28 on 2026-08-30.
-- For an empty database only. Do not apply to an existing ServiceHub database.
-- Flowable ACT_*/FLW_*/IDM_* tables remain owned by Flowable and are created at application startup.

-- -----------------------------------------------------------------------------
-- Source migration: V1__ticket_core.sql
-- -----------------------------------------------------------------------------
-- MySQL 8.0 baseline. All business timestamps are stored in UTC by the application.
CREATE TABLE ticket_number_sequence (
    business_date DATE NOT NULL,
    sequence_value INT UNSIGNED NOT NULL,
    PRIMARY KEY (business_date),
    CONSTRAINT ck_ticket_number_sequence_positive CHECK (sequence_value > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ticket (
    id VARCHAR(24) NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority VARCHAR(8) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    structured_fields JSON NOT NULL,
    tags JSON NOT NULL,
    related_configuration_item_ids JSON NOT NULL,
    requester_iam_user_id VARCHAR(128) NOT NULL,
    requester_display_name VARCHAR(100) NOT NULL,
    requester_organization_name VARCHAR(200) NOT NULL,
    requester_position_name VARCHAR(200) NULL,
    requester_captured_at DATETIME(6) NOT NULL,
    service_catalog_item_id VARCHAR(64) NOT NULL,
    service_catalog_item_name VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ticket_requester_created (requester_iam_user_id, created_at DESC),
    KEY idx_ticket_status_created (status, created_at DESC),
    KEY idx_ticket_type_created (type, created_at DESC),
    KEY idx_ticket_catalog_created (service_catalog_item_id, created_at DESC),
    CONSTRAINT ck_ticket_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE ticket_idempotency (
    actor_iam_user_id VARCHAR(128) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (actor_iam_user_id, idempotency_key),
    UNIQUE KEY uk_ticket_idempotency_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Designed for the next audit/outbox implementation. No event body is accepted from browsers.
CREATE TABLE audit_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(128) NOT NULL,
    attributes JSON NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_event_resource (resource_type, resource_id, occurred_at DESC),
    KEY idx_audit_event_actor (actor_iam_user_id, occurred_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V2__iam_read_only_projections.sql
-- -----------------------------------------------------------------------------
-- IAM is the authoritative identity source. These tables are local, read-only projections used
-- for assignment, data-scope checks, message delivery and immutable business snapshots. They
-- deliberately contain no password, credential, token or local-account columns.
CREATE TABLE iam_sync_batch (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    organization_count INT UNSIGNED NOT NULL DEFAULT 0,
    user_count INT UNSIGNED NOT NULL DEFAULT 0,
    failure_summary VARCHAR(500) NULL,
    PRIMARY KEY (id),
    KEY idx_iam_sync_batch_source_started (source_system, started_at DESC),
    CONSTRAINT ck_iam_sync_batch_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'PARTIAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_organization_projection (
    iam_organization_id VARCHAR(128) NOT NULL,
    organization_code VARCHAR(128) NULL,
    organization_name VARCHAR(200) NOT NULL,
    parent_iam_organization_id VARCHAR(128) NULL,
    organization_path VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_organization_id),
    KEY idx_iam_organization_parent (parent_iam_organization_id),
    KEY idx_iam_organization_code (organization_code),
    KEY idx_iam_organization_active_path (active, organization_path(255)),
    CONSTRAINT fk_iam_organization_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_projection (
    iam_user_id VARCHAR(128) NOT NULL,
    login_name VARCHAR(128) NULL,
    employee_number VARCHAR(128) NULL,
    display_name VARCHAR(100) NOT NULL,
    work_email VARCHAR(254) NULL,
    work_mobile VARCHAR(64) NULL,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id),
    KEY idx_iam_user_login_name (login_name),
    KEY idx_iam_user_employee_number (employee_number),
    KEY idx_iam_user_active_display_name (active, display_name),
    CONSTRAINT fk_iam_user_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE iam_user_organization_position_projection (
    iam_user_id VARCHAR(128) NOT NULL,
    iam_organization_id VARCHAR(128) NOT NULL,
    iam_position_id VARCHAR(128) NOT NULL,
    position_name VARCHAR(200) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    last_sync_batch_id BIGINT UNSIGNED NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, iam_organization_id, iam_position_id),
    KEY idx_iam_user_org_primary (iam_user_id, active, is_primary),
    KEY idx_iam_org_user_active (iam_organization_id, active, iam_user_id),
    CONSTRAINT fk_iam_user_org_position_user FOREIGN KEY (iam_user_id) REFERENCES iam_user_projection (iam_user_id),
    CONSTRAINT fk_iam_user_org_position_organization FOREIGN KEY (iam_organization_id) REFERENCES iam_organization_projection (iam_organization_id),
    CONSTRAINT fk_iam_user_org_position_sync_batch FOREIGN KEY (last_sync_batch_id) REFERENCES iam_sync_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V3__service_catalog_and_case_matching.sql
-- -----------------------------------------------------------------------------
-- Service catalog is configuration data managed through a later, separately-authorized console.
-- Requesters may read only PUBLISHED records; no browser request is allowed to supply a form definition.
CREATE TABLE service_catalog_item (
    id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    supported_ticket_types JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_service_catalog_item_status (publication_status, name),
    CONSTRAINT ck_service_catalog_item_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_service_catalog_item_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_dictionary (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT ck_service_catalog_dictionary_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_service_catalog_dictionary_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_dictionary_option (
    dictionary_code VARCHAR(64) NOT NULL,
    option_code VARCHAR(128) NOT NULL,
    option_label VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (dictionary_code, option_code),
    CONSTRAINT fk_catalog_dictionary_option_dictionary FOREIGN KEY (dictionary_code)
        REFERENCES service_catalog_dictionary (code),
    CONSTRAINT ck_service_catalog_dictionary_option_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_form_field (
    catalog_item_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    max_length INT NULL,
    dictionary_code VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (catalog_item_id, field_code),
    KEY idx_service_catalog_form_field_dictionary (dictionary_code),
    CONSTRAINT fk_catalog_form_field_item FOREIGN KEY (catalog_item_id) REFERENCES service_catalog_item (id),
    CONSTRAINT fk_catalog_form_field_dictionary FOREIGN KEY (dictionary_code) REFERENCES service_catalog_dictionary (code),
    CONSTRAINT ck_service_catalog_form_field_type CHECK (field_type IN ('TEXT', 'SINGLE_SELECT', 'MULTI_SELECT', 'CI_ID')),
    CONSTRAINT ck_service_catalog_form_field_max_length CHECK (max_length IS NULL OR max_length BETWEEN 1 AND 4000),
    CONSTRAINT ck_service_catalog_form_field_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Standard labels are centrally governed. Free labels remain restricted by the API's #tag grammar.
CREATE TABLE service_catalog_tag (
    tag_name VARCHAR(51) NOT NULL,
    tag_label VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_case (
    id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    resolution_summary VARCHAR(2000) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    published_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_case_status (publication_status, updated_at DESC),
    CONSTRAINT ck_knowledge_case_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One rule is an AND group. A matching rule can contribute one candidate and never changes ticket state.
CREATE TABLE knowledge_case_match_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    knowledge_case_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_item_id VARCHAR(64) NULL,
    configuration_item_id VARCHAR(128) NULL,
    field_code VARCHAR(64) NULL,
    field_value VARCHAR(4000) NULL,
    tag_name VARCHAR(51) NULL,
    tag_kind VARCHAR(16) NULL,
    error_code VARCHAR(128) NULL,
    keyword VARCHAR(200) NULL,
    score INT NOT NULL DEFAULT 50,
    PRIMARY KEY (id),
    KEY idx_case_match_rule_case (knowledge_case_id, enabled),
    KEY idx_case_match_rule_catalog (catalog_item_id, enabled),
    CONSTRAINT fk_case_match_rule_case FOREIGN KEY (knowledge_case_id) REFERENCES knowledge_case (id),
    CONSTRAINT fk_case_match_rule_catalog FOREIGN KEY (catalog_item_id) REFERENCES service_catalog_item (id),
    CONSTRAINT ck_case_match_rule_tag_kind CHECK (tag_kind IS NULL OR tag_kind IN ('STANDARD', 'FREE')),
    CONSTRAINT ck_case_match_rule_score CHECK (score BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The request snapshot deliberately stores matching criteria, not browser/session credentials or full free-text descriptions.
CREATE TABLE knowledge_case_match_record (
    id CHAR(36) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    catalog_item_id VARCHAR(64) NOT NULL,
    criteria_hash CHAR(64) NOT NULL,
    matched_case_ids JSON NOT NULL,
    matched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_case_match_record_actor_time (actor_iam_user_id, matched_at DESC),
    KEY idx_case_match_record_catalog_time (catalog_item_id, matched_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V4__ticket_workflow_and_collaboration.sql
-- -----------------------------------------------------------------------------
-- Workflow business projection. Flowable ACT_* tables remain engine-owned; this schema is the
-- application authority for collaboration, optimistic versions and auditable jump applications.
CREATE TABLE ticket_workflow_instance (
    ticket_id VARCHAR(24) NOT NULL,
    engine_instance_id VARCHAR(64) NOT NULL,
    current_node VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    resume_status VARCHAR(32) NULL,
    escalation_level INT NOT NULL DEFAULT 0,
    primary_assignee_iam_user_id VARCHAR(128) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id),
    UNIQUE KEY uk_ticket_workflow_engine_instance (engine_instance_id),
    KEY idx_ticket_workflow_assignee (primary_assignee_iam_user_id, updated_at DESC),
    CONSTRAINT ck_ticket_workflow_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_ticket_workflow_escalation_nonnegative CHECK (escalation_level >= 0),
    CONSTRAINT fk_ticket_workflow_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_workflow_task (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    engine_task_id VARCHAR(64) NULL,
    node_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    candidate_role VARCHAR(64) NULL,
    candidate_iam_user_id VARCHAR(128) NULL,
    assignee_iam_user_id VARCHAR(128) NULL,
    collaboration_role VARCHAR(16) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_workflow_task_open (ticket_id, node_key, status),
    KEY idx_ticket_workflow_task_assignee (assignee_iam_user_id, status, updated_at DESC),
    CONSTRAINT ck_ticket_workflow_task_version_nonnegative CHECK (version >= 0),
    CONSTRAINT fk_ticket_workflow_task_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_co_handler (
    ticket_id VARCHAR(24) NOT NULL,
    iam_user_id VARCHAR(128) NOT NULL,
    added_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, iam_user_id),
    CONSTRAINT fk_ticket_co_handler_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_internal_comment (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    author_iam_user_id VARCHAR(128) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_internal_comment_ticket (ticket_id, created_at),
    CONSTRAINT fk_ticket_internal_comment_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_controlled_jump_request (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    applicant_iam_user_id VARCHAR(128) NOT NULL,
    source_node VARCHAR(64) NOT NULL,
    target_node VARCHAR(64) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_controlled_jump_pending (status, created_at),
    CONSTRAINT fk_ticket_controlled_jump_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_workflow_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ticket_id VARCHAR(24) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    attributes JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_workflow_event_ticket (ticket_id, occurred_at DESC),
    CONSTRAINT fk_ticket_workflow_event_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V5__notification_outbox.sql
-- -----------------------------------------------------------------------------
-- Notifications are a local delivery projection.  Recipient identities are IAM user IDs only;
-- no local account, password, external token or browser-supplied recipient is persisted here.
CREATE TABLE notification_channel_route_rule (
    id VARCHAR(64) NOT NULL,
    iam_organization_id VARCHAR(128) NOT NULL,
    include_descendants BOOLEAN NOT NULL DEFAULT FALSE,
    event_type VARCHAR(64) NOT NULL DEFAULT '*',
    preferred_channel VARCHAR(32) NOT NULL,
    provider_channel_code VARCHAR(128) NULL,
    fallback_channel VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_route_match (iam_organization_id, enabled, priority),
    CONSTRAINT ck_notification_route_priority CHECK (priority >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seed rows demonstrate organisation-specific WPS channel mappings. WPS delivery remains
-- disabled; these values are routing aliases, not endpoint addresses, recipients or credentials.
INSERT INTO notification_channel_route_rule (id, iam_organization_id, include_descendants, event_type, preferred_channel, provider_channel_code, fallback_channel, priority, enabled, version, created_at, updated_at)
VALUES ('ROUTE-IT-WPS', 'org-it', FALSE, '*', 'WPS_IM', 'wps-it-service-desk', 'IN_APP', 10, TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
       ('ROUTE-FINANCE-WPS', 'org-finance', FALSE, '*', 'WPS_IM', 'wps-finance-service-desk', 'IN_APP', 10, TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

CREATE TABLE notification (
    id VARCHAR(80) NOT NULL,
    recipient_iam_user_id VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    ticket_id VARCHAR(24) NULL,
    payload JSON NOT NULL,
    routing_snapshot JSON NOT NULL,
    deduplication_key VARCHAR(128) NOT NULL,
    read_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient_unread (recipient_iam_user_id, read_at, created_at DESC),
    KEY idx_notification_ticket (ticket_id, created_at DESC),
    UNIQUE KEY uk_notification_deduplication (deduplication_key),
    CONSTRAINT fk_notification_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_delivery (
    id VARCHAR(80) NOT NULL,
    notification_id VARCHAR(80) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    provider_channel_code VARCHAR(128) NULL,
    route_rule_id VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    provider_message_id VARCHAR(256) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    delivered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notification_delivery_channel (notification_id, channel),
    KEY idx_notification_delivery_retry (status, next_attempt_at),
    CONSTRAINT ck_notification_delivery_attempts_nonnegative CHECK (attempt_count >= 0),
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES notification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The dispatcher is deliberately decoupled from the request transaction.  A worker may claim
-- PENDING rows using a lease in a later release; browser requests must never call IM providers.
CREATE TABLE message_outbox (
    id VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    last_error VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_message_outbox_dispatch (status, available_at, created_at),
    CONSTRAINT ck_message_outbox_attempts_nonnegative CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notification_preference (
    iam_user_id VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (iam_user_id, channel, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V6__attachment_and_knowledge_import.sql
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- Source migration: V7__sla_and_operational_reporting.sql
-- -----------------------------------------------------------------------------
-- SLA is an application-owned projection. Flowable and notification tables are deliberately not
-- used as reporting sources: daily summaries keep dashboard reads bounded.
ALTER TABLE ticket ADD COLUMN requester_organization_id VARCHAR(128) NULL AFTER requester_display_name;
UPDATE ticket SET requester_organization_id = '' WHERE requester_organization_id IS NULL;
ALTER TABLE ticket MODIFY requester_organization_id VARCHAR(128) NOT NULL;

CREATE TABLE sla_policy (
    id CHAR(36) NOT NULL,
    policy_name VARCHAR(120) NOT NULL,
    service_catalog_item_id VARCHAR(64) NULL,
    priority VARCHAR(8) NULL,
    organization_scope_id VARCHAR(128) NULL,
    response_target_minutes INT UNSIGNED NOT NULL,
    resolution_target_minutes INT UNSIGNED NOT NULL,
    calendar_key VARCHAR(64) NOT NULL,
    pause_statuses JSON NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sla_policy_name (policy_name),
    KEY idx_sla_policy_match (active, service_catalog_item_id, priority, organization_scope_id),
    CONSTRAINT ck_sla_response_target_positive CHECK (response_target_minutes > 0),
    CONSTRAINT ck_sla_resolution_target_positive CHECK (resolution_target_minutes > 0),
    CONSTRAINT ck_sla_policy_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sla_policy (id, policy_name, service_catalog_item_id, priority, organization_scope_id,
                        response_target_minutes, resolution_target_minutes, calendar_key, pause_statuses, active, version, created_at, updated_at)
VALUES ('00000000-0000-4000-8000-000000000007', '默认 P3 服务台', NULL, 'P3', NULL, 60, 480, '24X7', JSON_ARRAY('ON_HOLD', 'PENDING_USER_FEEDBACK'), TRUE, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

CREATE TABLE ticket_sla_target (
    ticket_id VARCHAR(24) NOT NULL,
    policy_id CHAR(36) NOT NULL,
    policy_name_snapshot VARCHAR(120) NOT NULL,
    calendar_key_snapshot VARCHAR(64) NOT NULL,
    response_due_at DATETIME(6) NOT NULL,
    resolution_due_at DATETIME(6) NOT NULL,
    first_responded_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    paused_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
    pause_started_at DATETIME(6) NULL,
    risk_level VARCHAR(16) NOT NULL,
    response_breached BOOLEAN NOT NULL DEFAULT FALSE,
    resolution_breached BOOLEAN NOT NULL DEFAULT FALSE,
    calculated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (ticket_id),
    KEY idx_ticket_sla_risk (risk_level, resolution_due_at),
    KEY idx_ticket_sla_breach (resolution_breached, resolution_due_at),
    CONSTRAINT fk_ticket_sla_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_sla_policy FOREIGN KEY (policy_id) REFERENCES sla_policy(id),
    CONSTRAINT ck_ticket_sla_paused_seconds_nonnegative CHECK (paused_seconds >= 0),
    CONSTRAINT ck_ticket_sla_version_nonnegative CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_report_refresh_job (
    job_key VARCHAR(64) NOT NULL,
    last_started_at DATETIME(6) NULL,
    last_succeeded_at DATETIME(6) NULL,
    last_status VARCHAR(16) NOT NULL,
    last_error_code VARCHAR(64) NULL,
    refreshed_from DATE NULL,
    refreshed_to DATE NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (job_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO operation_report_refresh_job (job_key, last_status, updated_at)
VALUES ('daily-ticket-kpi', 'NEVER', UTC_TIMESTAMP(6));

CREATE TABLE operation_daily_ticket_kpi (
    business_date DATE NOT NULL,
    requester_organization_id VARCHAR(128) NOT NULL,
    ticket_status VARCHAR(32) NOT NULL,
    ticket_volume INT UNSIGNED NOT NULL,
    open_volume INT UNSIGNED NOT NULL,
    response_seconds_sum BIGINT UNSIGNED NOT NULL DEFAULT 0,
    response_sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    resolution_seconds_sum BIGINT UNSIGNED NOT NULL DEFAULT 0,
    resolution_sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    at_risk_volume INT UNSIGNED NOT NULL DEFAULT 0,
    breached_volume INT UNSIGNED NOT NULL DEFAULT 0,
    refreshed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (business_date, requester_organization_id, ticket_status),
    KEY idx_operation_daily_kpi_date_org (business_date, requester_organization_id),
    CONSTRAINT ck_operation_daily_ticket_volume CHECK (ticket_volume >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V8__external_integration_security_foundation.sql
-- -----------------------------------------------------------------------------
-- V8 deliberately stores only connection metadata and CMDB read-only projections.  Secrets are
-- opaque vault/KMS references; raw alert payloads, tokens and external endpoint credentials are
-- never persisted by the service desk.
CREATE TABLE external_connection_configuration (
    connection_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    system_type VARCHAR(32) NOT NULL,
    trusted_base_url VARCHAR(512) NOT NULL,
    secret_ref VARCHAR(256) NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms INT UNSIGNED NOT NULL,
    rate_limit_per_minute INT UNSIGNED NOT NULL,
    allowed_callback_source_ips JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (connection_code),
    CONSTRAINT ck_external_connection_type CHECK (system_type IN ('CMDB', 'MONITORING', 'LOG_PLATFORM', 'APM')),
    CONSTRAINT ck_external_connection_timeout CHECK (timeout_ms BETWEEN 100 AND 30000),
    CONSTRAINT ck_external_connection_rate CHECK (rate_limit_per_minute BETWEEN 1 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cmdb_configuration_item_projection (
    ci_id VARCHAR(128) NOT NULL,
    source_code VARCHAR(40) NOT NULL,
    ci_name VARCHAR(240) NOT NULL,
    ci_type VARCHAR(80) NOT NULL,
    ci_status VARCHAR(64) NOT NULL,
    organization_id VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    source_version VARCHAR(128) NOT NULL,
    synced_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ci_id),
    KEY idx_cmdb_ci_organization (organization_id, active, ci_name),
    KEY idx_cmdb_ci_source (source_code, active),
    CONSTRAINT fk_cmdb_ci_connection FOREIGN KEY (source_code) REFERENCES external_connection_configuration(connection_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_configuration_item (
    ticket_id VARCHAR(24) NOT NULL,
    ci_id VARCHAR(128) NOT NULL,
    linked_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, ci_id),
    KEY idx_ticket_ci_ci (ci_id, ticket_id),
    CONSTRAINT fk_ticket_ci_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_ci_projection FOREIGN KEY (ci_id) REFERENCES cmdb_configuration_item_projection(ci_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE external_normalized_alert (
    id VARCHAR(48) NOT NULL,
    source_code VARCHAR(40) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    fingerprint VARCHAR(128) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(240) NOT NULL,
    ci_id VARCHAR(128) NULL,
    alert_status VARCHAR(32) NOT NULL,
    idempotency_status VARCHAR(24) NOT NULL,
    ticket_id VARCHAR(24) NULL,
    occurred_at DATETIME(6) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_alert_source_event (source_code, source_event_id),
    KEY idx_external_alert_recent (received_at),
    KEY idx_external_alert_ci (ci_id, occurred_at),
    KEY idx_external_alert_ticket (ticket_id, occurred_at),
    CONSTRAINT fk_external_alert_source FOREIGN KEY (source_code) REFERENCES external_connection_configuration(connection_code),
    CONSTRAINT fk_external_alert_ci FOREIGN KEY (ci_id) REFERENCES cmdb_configuration_item_projection(ci_id),
    CONSTRAINT fk_external_alert_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT ck_external_alert_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V9__ticket_relations.sql
-- -----------------------------------------------------------------------------
-- A controlled, audited relationship graph.  No hard deletes are exposed by the application.
CREATE TABLE ticket_relation (
    ticket_id VARCHAR(24) NOT NULL,
    related_ticket_id VARCHAR(24) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, related_ticket_id, relation_type),
    KEY idx_ticket_relation_related (related_ticket_id, created_at DESC),
    CONSTRAINT ck_ticket_relation_distinct CHECK (ticket_id <> related_ticket_id),
    CONSTRAINT fk_ticket_relation_source FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_relation_target FOREIGN KEY (related_ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V10__workflow_participant_snapshots.sql
-- -----------------------------------------------------------------------------
CREATE TABLE ticket_workflow_participant (
    ticket_id VARCHAR(24) NOT NULL,
    role VARCHAR(24) NOT NULL,
    iam_user_id VARCHAR(128) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    organization_id VARCHAR(128) NULL,
    organization_name VARCHAR(200) NOT NULL,
    position_name VARCHAR(200) NULL,
    captured_at DATETIME(6) NOT NULL,
    assigned_at DATETIME(6) NOT NULL,
    active BOOLEAN NOT NULL,
    unassigned_at DATETIME(6) NULL,
    PRIMARY KEY (ticket_id, role, iam_user_id),
    KEY idx_ticket_workflow_participant_active (ticket_id, active, role),
    CONSTRAINT fk_ticket_workflow_participant_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V11__controlled_jump_approval_decisions.sql
-- -----------------------------------------------------------------------------
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approver_iam_user_id VARCHAR(128) NULL AFTER status,
    ADD COLUMN decision_reason VARCHAR(1000) NULL AFTER approver_iam_user_id,
    ADD COLUMN decided_at DATETIME(6) NULL AFTER decision_reason;


-- -----------------------------------------------------------------------------
-- Source migration: V12__controlled_jump_flowable_instance.sql
-- -----------------------------------------------------------------------------
ALTER TABLE ticket_controlled_jump_request ADD COLUMN approval_engine_instance_id VARCHAR(128) NULL AFTER id;


-- -----------------------------------------------------------------------------
-- Source migration: V13__controlled_jump_source_versions.sql
-- -----------------------------------------------------------------------------
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN source_ticket_version BIGINT NULL AFTER source_node,
    ADD COLUMN source_workflow_version BIGINT NULL AFTER source_ticket_version;


-- -----------------------------------------------------------------------------
-- Source migration: V14__controlled_jump_execution_result.sql
-- -----------------------------------------------------------------------------
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN executor_iam_user_id VARCHAR(128) NULL AFTER source_workflow_version,
    ADD COLUMN execution_started_at TIMESTAMP NULL AFTER executor_iam_user_id,
    ADD COLUMN executed_at TIMESTAMP NULL AFTER execution_started_at,
    ADD COLUMN executed_from_node VARCHAR(64) NULL AFTER executed_at,
    ADD COLUMN executed_to_node VARCHAR(64) NULL AFTER executed_from_node,
    ADD COLUMN execution_failure_reason VARCHAR(160) NULL AFTER executed_to_node;

CREATE INDEX idx_controlled_jump_execution ON ticket_controlled_jump_request (ticket_id, status, execution_started_at);


-- -----------------------------------------------------------------------------
-- Source migration: V15__iam_user_role_projection.sql
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- Source migration: V16__controlled_jump_approval_policy_snapshot.sql
-- -----------------------------------------------------------------------------
-- Freeze the policy that governed each approval request. New definitions or role mappings must
-- never silently rewrite an in-flight or historical approval decision.
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approval_process_key VARCHAR(128) NULL AFTER approval_engine_instance_id,
    ADD COLUMN approval_process_definition_id VARCHAR(128) NULL AFTER approval_process_key,
    ADD COLUMN approval_process_version INT UNSIGNED NULL AFTER approval_process_definition_id,
    ADD COLUMN approval_candidate_roles_json JSON NULL AFTER approval_process_version,
    ADD COLUMN approval_decision_mode VARCHAR(32) NULL AFTER approval_candidate_roles_json,
    ADD COLUMN approval_timeout_policy_version VARCHAR(128) NULL AFTER approval_decision_mode,
    ADD COLUMN approval_escalation_policy_version VARCHAR(128) NULL AFTER approval_timeout_policy_version,
    ADD COLUMN approval_policy_captured_at DATETIME(6) NULL AFTER approval_escalation_policy_version;

-- Historical records cannot safely claim a concrete Flowable definition id. Mark them explicitly
-- so application services can fail closed instead of silently applying the current definition.
UPDATE ticket_controlled_jump_request
   SET approval_process_key = 'servicehubControlledJumpApproval',
       approval_process_definition_id = 'LEGACY_UNRECORDED',
       approval_process_version = 0,
       approval_candidate_roles_json = JSON_ARRAY('ROLE_SERVICE_MANAGER', 'ROLE_PLATFORM_ADMIN'),
       approval_decision_mode = 'ANY_ONE',
       approval_timeout_policy_version = 'NONE',
       approval_escalation_policy_version = 'NONE',
       approval_policy_captured_at = created_at
 WHERE approval_process_key IS NULL;

ALTER TABLE ticket_controlled_jump_request
    MODIFY COLUMN approval_process_key VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_process_definition_id VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_process_version INT UNSIGNED NOT NULL,
    MODIFY COLUMN approval_candidate_roles_json JSON NOT NULL,
    MODIFY COLUMN approval_decision_mode VARCHAR(32) NOT NULL,
    MODIFY COLUMN approval_timeout_policy_version VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_escalation_policy_version VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_policy_captured_at DATETIME(6) NOT NULL;


-- -----------------------------------------------------------------------------
-- Source migration: V17__controlled_jump_approval_decision_events.sql
-- -----------------------------------------------------------------------------
-- Append-only decision history. It is separate from the current request projection so a future
-- multi-instance countersign process can retain every decision without overwriting prior facts.
CREATE TABLE ticket_controlled_jump_approval_decision_event (
    id VARCHAR(36) NOT NULL,
    approval_request_id VARCHAR(36) NOT NULL,
    engine_task_id VARCHAR(128) NULL,
    approver_iam_user_id VARCHAR(128) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    decided_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_jump_approval_decision_engine_task (approval_request_id, engine_task_id),
    KEY idx_jump_approval_decision_request_time (approval_request_id, decided_at),
    CONSTRAINT fk_jump_approval_decision_request FOREIGN KEY (approval_request_id) REFERENCES ticket_controlled_jump_request (id),
    CONSTRAINT ck_jump_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- -----------------------------------------------------------------------------
-- Source migration: V18__approval_policy_candidate_user_snapshot.sql
-- -----------------------------------------------------------------------------
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approval_candidate_iam_user_ids_json JSON NULL AFTER approval_candidate_roles_json;

UPDATE ticket_controlled_jump_request
   SET approval_candidate_iam_user_ids_json = JSON_ARRAY()
 WHERE approval_candidate_iam_user_ids_json IS NULL;

ALTER TABLE ticket_controlled_jump_request
    MODIFY COLUMN approval_candidate_iam_user_ids_json JSON NOT NULL;


-- -----------------------------------------------------------------------------
-- Source migration: V19__service_announcements.sql
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- Source migration: V20__backoffice_access_authorization.sql
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- Source migration: V21__append_only_audit_event.sql
-- -----------------------------------------------------------------------------
-- V1 already created the original audit_event table.  This baseline preserves
-- the cumulative V1–V26 outcome by applying the V21 in-place transformation.
ALTER TABLE audit_event
    MODIFY COLUMN id CHAR(36) NOT NULL,
    MODIFY COLUMN resource_type VARCHAR(96) NOT NULL,
    MODIFY COLUMN resource_id VARCHAR(160) NOT NULL,
    CHANGE COLUMN action action_code VARCHAR(128) NOT NULL,
    CHANGE COLUMN attributes attributes_json JSON NOT NULL,
    DROP INDEX idx_audit_event_resource,
    DROP INDEX idx_audit_event_actor,
    ADD KEY idx_audit_event_occurred_at (occurred_at),
    ADD KEY idx_audit_event_actor_time (actor_iam_user_id, occurred_at),
    ADD KEY idx_audit_event_resource_time (resource_type, resource_id, occurred_at),
    ADD KEY idx_audit_event_action_time (action_code, occurred_at);


-- -----------------------------------------------------------------------------
-- Source migration: V22__ticket_rich_text_description.sql
-- -----------------------------------------------------------------------------
-- Rich-text is stored only after server-side allow-list sanitization. Existing tickets remain plain text.
ALTER TABLE ticket
    ADD COLUMN description_format VARCHAR(16) NOT NULL DEFAULT 'PLAIN_TEXT' AFTER description,
    ADD COLUMN description_html MEDIUMTEXT NULL AFTER description_format;


-- -----------------------------------------------------------------------------
-- Source migration: V23__service_catalog_form_configuration.sql
-- -----------------------------------------------------------------------------
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


-- -----------------------------------------------------------------------------
-- Source migration: V24__ticket_service_catalog_form_version.sql
-- -----------------------------------------------------------------------------
-- Ticket submission is bound to the actual published schema revision, so future form changes cannot rewrite history.
ALTER TABLE ticket ADD COLUMN service_catalog_form_version INT NOT NULL DEFAULT 1 AFTER service_catalog_item_name;
ALTER TABLE ticket ADD CONSTRAINT ck_ticket_service_catalog_form_version CHECK (service_catalog_form_version >= 1);


-- -----------------------------------------------------------------------------
-- Source migration: V25__service_catalog_form_configuration_history.sql
-- -----------------------------------------------------------------------------
-- Published schema snapshots are append-only, allowing rollback to create a new draft without rewriting history.
CREATE TABLE service_catalog_form_configuration_history LIKE service_catalog_form_configuration;
ALTER TABLE service_catalog_form_configuration_history DROP PRIMARY KEY;
ALTER TABLE service_catalog_form_configuration_history DROP INDEX uk_service_catalog_form_configuration_code;
ALTER TABLE service_catalog_form_configuration_history ADD PRIMARY KEY (code, form_version);


-- -----------------------------------------------------------------------------
-- Source migration: V26__ticket_workflow_definition_snapshot.sql
-- -----------------------------------------------------------------------------
-- The business projection keeps the exact Flowable definition that started the current
-- lifecycle instance. Existing rows remain readable as legacy history; new starts and reopen
-- operations always capture both fields before any ticket action is accepted.
ALTER TABLE ticket_workflow_instance
    ADD COLUMN process_definition_id VARCHAR(128) NULL AFTER primary_assignee_iam_user_id,
    ADD COLUMN process_definition_version INT NULL AFTER process_definition_id;


-- -----------------------------------------------------------------------------
-- Source migration: V27__ticket_handover_confirmation.sql
-- -----------------------------------------------------------------------------
-- A handover is a two-party, Flowable-backed confirmation. The source snapshot prevents a
-- confirmation from silently changing an already progressed ticket.
CREATE TABLE ticket_handover_request (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    engine_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(128) NOT NULL,
    process_definition_version INT NOT NULL,
    applicant_iam_user_id VARCHAR(128) NOT NULL,
    target_iam_user_id VARCHAR(128) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_ticket_version BIGINT NOT NULL,
    source_workflow_version BIGINT NOT NULL,
    decided_at DATETIME(6) NULL,
    decision_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ticket_handover_engine (engine_instance_id),
    KEY idx_ticket_handover_ticket (ticket_id, created_at DESC),
    KEY idx_ticket_handover_target (target_iam_user_id, status, created_at DESC),
    CONSTRAINT ck_ticket_handover_status CHECK (status IN ('PENDING_CONFIRMATION', 'ACCEPTED', 'REJECTED', 'STALE')),
    CONSTRAINT fk_ticket_handover_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;






-- -----------------------------------------------------------------------------
-- Source migration: V28__lifecycle_action_approval.sql
-- -----------------------------------------------------------------------------
-- High-risk lifecycle actions are intentionally isolated from controlled-jump approvals.
-- The request captures the immutable source and Flowable definition selected at submission.
CREATE TABLE ticket_lifecycle_action_approval_request (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    applicant_iam_user_id VARCHAR(128) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    source_ticket_version BIGINT NOT NULL,
    source_workflow_version BIGINT NOT NULL,
    approval_engine_instance_id VARCHAR(64) NOT NULL,
    approval_process_key VARCHAR(128) NOT NULL,
    approval_process_definition_id VARCHAR(128) NOT NULL,
    approval_process_version INT NOT NULL,
    approval_candidate_roles_json JSON NOT NULL,
    approval_candidate_iam_user_ids_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    approver_iam_user_id VARCHAR(128) NULL,
    decision_reason VARCHAR(1000) NULL,
    decided_at DATETIME(6) NULL,
    executed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lifecycle_action_approval_engine (approval_engine_instance_id),
    KEY idx_lifecycle_action_approval_ticket (ticket_id, created_at DESC),
    KEY idx_lifecycle_action_approval_status (status, created_at DESC),
    CONSTRAINT ck_lifecycle_action_approval_action CHECK (action_code IN ('HOLD', 'ESCALATE', 'CANCEL', 'REOPEN')),
    CONSTRAINT ck_lifecycle_action_approval_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'EXECUTED', 'STALE')),
    CONSTRAINT fk_lifecycle_action_approval_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
