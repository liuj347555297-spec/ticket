-- Service-catalog routing policy and immutable application snapshot.  Browser clients never write either table.
CREATE TABLE workflow_node_assignment_policy (
    service_catalog_item_id VARCHAR(64) NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    assignment_mode VARCHAR(32) NOT NULL,
    candidate_roles_json JSON NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (service_catalog_item_id, node_key),
    CONSTRAINT ck_workflow_node_assignment_mode CHECK (assignment_mode IN ('SYSTEM_RANDOM','PREVIOUS_HANDLER_SELECTS')),
    CONSTRAINT ck_workflow_node_assignment_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ticket_workflow_node_assignment_snapshot (
    ticket_id VARCHAR(32) NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    assignment_mode VARCHAR(32) NOT NULL,
    candidate_roles_json JSON NOT NULL,
    policy_version BIGINT NOT NULL,
    selected_iam_user_id VARCHAR(128) NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, node_key),
    KEY idx_ticket_workflow_assignment_selected (selected_iam_user_id, captured_at),
    CONSTRAINT ck_ticket_workflow_assignment_mode CHECK (assignment_mode IN ('SYSTEM_RANDOM','PREVIOUS_HANDLER_SELECTS')),
    CONSTRAINT ck_ticket_workflow_assignment_version CHECK (policy_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
