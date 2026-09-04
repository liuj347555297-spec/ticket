-- Published policy versions are the only source of lifecycle-approval mode, scope and deadline.
-- A request copies the effective policy; later policy edits can never rewrite an in-flight approval.
CREATE TABLE lifecycle_approval_policy (
    id CHAR(36) NOT NULL,
    policy_name VARCHAR(120) NOT NULL,
    action_code VARCHAR(32) NOT NULL,
    service_catalog_item_id VARCHAR(128) NULL,
    ticket_priority VARCHAR(16) NULL,
    candidate_roles_json JSON NOT NULL,
    decision_mode VARCHAR(16) NOT NULL,
    approval_threshold_percent TINYINT UNSIGNED NOT NULL,
    timeout_minutes INT UNSIGNED NOT NULL,
    timeout_policy_version VARCHAR(64) NOT NULL,
    escalation_policy_version VARCHAR(64) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_lifecycle_approval_policy_match (lifecycle_status, action_code, service_catalog_item_id, ticket_priority),
    CONSTRAINT ck_lifecycle_approval_policy_action CHECK (action_code IN ('HOLD','ESCALATE','CANCEL','REOPEN','ASSIGN','ACCEPT','RESOLVE','CLOSE')),
    CONSTRAINT ck_lifecycle_approval_policy_mode CHECK (decision_mode IN ('ANY_ONE','ALL_OF','QUORUM')),
    CONSTRAINT ck_lifecycle_approval_policy_threshold CHECK (approval_threshold_percent BETWEEN 1 AND 100),
    CONSTRAINT ck_lifecycle_approval_policy_timeout CHECK (timeout_minutes BETWEEN 1 AND 43200),
    CONSTRAINT ck_lifecycle_approval_policy_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Safe baseline: explicit server-manager approval for every currently governed action.
INSERT INTO lifecycle_approval_policy
    (id, policy_name, action_code, service_catalog_item_id, ticket_priority, candidate_roles_json, decision_mode,
     approval_threshold_percent, timeout_minutes, timeout_policy_version, escalation_policy_version, lifecycle_status,
     version, created_at, updated_at, published_at)
VALUES
    ('00000000-0000-4000-8000-000000000032','默认生命周期审批','HOLD',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000033','默认生命周期审批','ESCALATE',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000034','默认生命周期审批','CANCEL',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000035','默认生命周期审批','REOPEN',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000036','默认生命周期审批','ASSIGN',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000037','默认生命周期审批','ACCEPT',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000038','默认生命周期审批','RESOLVE',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
    ('00000000-0000-4000-8000-000000000039','默认生命周期审批','CLOSE',NULL,NULL,JSON_ARRAY('ROLE_SERVICE_MANAGER','ROLE_PLATFORM_ADMIN'),'ANY_ONE',100,1440,'DEFAULT-24H-V1','AUDIT-ONLY-V1','PUBLISHED',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6));

ALTER TABLE ticket_lifecycle_action_approval_request
    ADD COLUMN approval_policy_id CHAR(36) NULL AFTER approval_process_version,
    ADD COLUMN approval_policy_version BIGINT NULL AFTER approval_policy_id,
    ADD COLUMN approval_decision_mode VARCHAR(16) NULL AFTER approval_policy_version,
    ADD COLUMN required_approval_count INT UNSIGNED NULL AFTER approval_decision_mode,
    ADD COLUMN approval_timeout_policy_version VARCHAR(64) NULL AFTER required_approval_count,
    ADD COLUMN approval_escalation_policy_version VARCHAR(64) NULL AFTER approval_timeout_policy_version,
    ADD COLUMN approval_due_at DATETIME(6) NULL AFTER approval_escalation_policy_version,
    ADD COLUMN expiration_claimed_at DATETIME(6) NULL AFTER approval_due_at;

UPDATE ticket_lifecycle_action_approval_request
   SET approval_policy_id = 'LEGACY-V28', approval_policy_version = 0, approval_decision_mode = 'ANY_ONE',
       required_approval_count = 1, approval_timeout_policy_version = 'LEGACY-UNRECORDED',
       approval_escalation_policy_version = 'LEGACY-UNRECORDED', approval_due_at = DATE_ADD(created_at, INTERVAL 1440 MINUTE)
 WHERE approval_policy_id IS NULL;

ALTER TABLE ticket_lifecycle_action_approval_request
    MODIFY COLUMN approval_policy_id VARCHAR(64) NOT NULL,
    MODIFY COLUMN approval_policy_version BIGINT NOT NULL,
    MODIFY COLUMN approval_decision_mode VARCHAR(16) NOT NULL,
    MODIFY COLUMN required_approval_count INT UNSIGNED NOT NULL,
    MODIFY COLUMN approval_timeout_policy_version VARCHAR(64) NOT NULL,
    MODIFY COLUMN approval_escalation_policy_version VARCHAR(64) NOT NULL,
    MODIFY COLUMN approval_due_at DATETIME(6) NOT NULL,
    DROP CHECK ck_lifecycle_action_approval_status,
    ADD CONSTRAINT ck_lifecycle_action_approval_status CHECK (status IN ('PENDING_APPROVAL','EXPIRING','EXPIRED','APPROVED','REJECTED','EXECUTED','STALE')),
    ADD CONSTRAINT ck_lifecycle_action_approval_mode CHECK (approval_decision_mode IN ('ANY_ONE','ALL_OF','QUORUM')),
    ADD CONSTRAINT ck_lifecycle_action_approval_required_count CHECK (required_approval_count >= 1),
    ADD KEY idx_lifecycle_action_approval_due (status, approval_due_at);

CREATE TABLE ticket_lifecycle_action_approval_timeout_event (
    id CHAR(36) NOT NULL,
    approval_request_id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    timeout_policy_version VARCHAR(64) NOT NULL,
    escalation_policy_version VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_lifecycle_action_approval_timeout (approval_request_id),
    CONSTRAINT fk_lifecycle_action_approval_timeout_request FOREIGN KEY (approval_request_id) REFERENCES ticket_lifecycle_action_approval_request(id),
    CONSTRAINT fk_lifecycle_action_approval_timeout_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
