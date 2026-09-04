-- Adding a co-handler is a consented collaboration assignment. The projection freezes the
-- source versions so a delayed confirmation cannot be applied to a progressed ticket.
CREATE TABLE ticket_cohandler_request (
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
    UNIQUE KEY uk_ticket_cohandler_engine (engine_instance_id),
    KEY idx_ticket_cohandler_ticket (ticket_id, created_at DESC),
    KEY idx_ticket_cohandler_target (target_iam_user_id, status, created_at DESC),
    CONSTRAINT ck_ticket_cohandler_request_status CHECK (status IN ('PENDING_CONFIRMATION', 'ACCEPTED', 'REJECTED', 'STALE')),
    CONSTRAINT fk_ticket_cohandler_request_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Delegation is deliberately ticket-scoped and time-boxed. It is authorization delegation, not
-- an approval: state-changing actions still run through the ticket lifecycle/Flowable boundary.
CREATE TABLE ticket_workflow_delegation (
    id CHAR(36) NOT NULL,
    ticket_id VARCHAR(24) NOT NULL,
    delegator_iam_user_id VARCHAR(128) NOT NULL,
    delegate_iam_user_id VARCHAR(128) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    effective_until DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ticket_delegation_active (ticket_id, delegate_iam_user_id, effective_from, effective_until),
    CONSTRAINT ck_ticket_delegation_no_self CHECK (delegator_iam_user_id <> delegate_iam_user_id),
    CONSTRAINT ck_ticket_delegation_window CHECK (effective_until > effective_from),
    CONSTRAINT fk_ticket_delegation_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
