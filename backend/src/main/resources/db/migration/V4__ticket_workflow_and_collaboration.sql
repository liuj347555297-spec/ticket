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
