-- Forward-only team queue model. Existing policies/tasks remain nullable and therefore compatible;
-- SHARED_QUEUE is enforced by the application to require an active queue before new routing.
CREATE TABLE support_team (
    team_code VARCHAR(64) NOT NULL,
    team_name VARCHAR(200) NOT NULL,
    owning_organization_id VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (team_code),
    KEY idx_support_team_org_status (owning_organization_id,status,team_code),
    CONSTRAINT ck_support_team_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','MIGRATION_PENDING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_queue (
    queue_code VARCHAR(64) NOT NULL,
    team_code VARCHAR(64) NOT NULL,
    queue_name VARCHAR(200) NOT NULL,
    shared_claim_enabled BOOLEAN NOT NULL,
    capacity_limit INT NULL,
    effective_from DATETIME(6) NOT NULL,
    effective_until DATETIME(6) NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (queue_code),
    KEY idx_support_queue_team_status (team_code,status,effective_from,effective_until),
    CONSTRAINT fk_support_queue_team FOREIGN KEY (team_code) REFERENCES support_team(team_code),
    CONSTRAINT ck_support_queue_status CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','MIGRATION_PENDING')),
    CONSTRAINT ck_support_queue_capacity CHECK (capacity_limit IS NULL OR capacity_limit BETWEEN 1 AND 100000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_team_member (
    team_code VARCHAR(64) NOT NULL,
    iam_user_id VARCHAR(128) NOT NULL,
    member_role VARCHAR(24) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    effective_until DATETIME(6) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (team_code,iam_user_id,member_role,effective_from),
    KEY idx_support_member_user_active (iam_user_id,active,effective_from,effective_until,team_code),
    CONSTRAINT fk_support_member_team FOREIGN KEY (team_code) REFERENCES support_team(team_code),
    CONSTRAINT ck_support_member_role CHECK (member_role IN ('MEMBER','SUPERVISOR')),
    CONSTRAINT ck_support_member_window CHECK (effective_until IS NULL OR effective_until>effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_queue_scope (
    queue_code VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_id VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (queue_code,scope_type,scope_id),
    KEY idx_support_queue_scope_reverse (scope_type,scope_id,active,queue_code),
    CONSTRAINT fk_support_scope_queue FOREIGN KEY (queue_code) REFERENCES support_queue(queue_code),
    CONSTRAINT ck_support_scope_type CHECK (scope_type IN ('ORGANIZATION','SERVICE_CATALOG','SERVICE_SYSTEM','CONFIGURATION_ITEM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE workflow_node_assignment_policy ADD COLUMN queue_code VARCHAR(64) NULL AFTER assignment_mode;
ALTER TABLE workflow_node_assignment_policy ADD CONSTRAINT fk_workflow_assignment_queue FOREIGN KEY (queue_code) REFERENCES support_queue(queue_code);
ALTER TABLE ticket_workflow_node_assignment_snapshot ADD COLUMN queue_code VARCHAR(64) NULL AFTER assignment_mode;
ALTER TABLE ticket_workflow_task ADD COLUMN queue_code VARCHAR(64) NULL AFTER node_key;
ALTER TABLE ticket_workflow_task ADD KEY idx_ticket_workflow_task_queue_open (queue_code,status,created_at,ticket_id);
ALTER TABLE ticket_workflow_task ADD CONSTRAINT fk_ticket_workflow_task_queue FOREIGN KEY (queue_code) REFERENCES support_queue(queue_code);

CREATE TABLE ticket_workflow_queue_routing_snapshot (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    workflow_task_id CHAR(36) NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    queue_code VARCHAR(64) NOT NULL,
    assignment_mode VARCHAR(32) NOT NULL,
    policy_version BIGINT NOT NULL,
    queue_version BIGINT NOT NULL,
    queue_scope_digest CHAR(43) NOT NULL,
    candidate_iam_user_ids_json JSON NOT NULL,
    ticket_context_digest CHAR(43) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_queue_routing_task (workflow_task_id),
    KEY idx_queue_routing_ticket (ticket_id,captured_at),
    KEY idx_queue_routing_queue (queue_code,captured_at),
    CONSTRAINT fk_queue_routing_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_queue_routing_task FOREIGN KEY (workflow_task_id) REFERENCES ticket_workflow_task(id),
    CONSTRAINT fk_queue_routing_queue FOREIGN KEY (queue_code) REFERENCES support_queue(queue_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
