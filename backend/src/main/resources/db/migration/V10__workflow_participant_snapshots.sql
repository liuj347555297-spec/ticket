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
