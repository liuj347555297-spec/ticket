-- A handover is a two-party, Flowable-backed confirmation.  The source snapshot prevents a
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
