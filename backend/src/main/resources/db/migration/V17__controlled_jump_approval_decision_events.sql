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
