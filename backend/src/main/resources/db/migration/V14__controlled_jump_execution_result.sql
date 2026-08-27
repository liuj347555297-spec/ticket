ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN executor_iam_user_id VARCHAR(128) NULL AFTER source_workflow_version,
    ADD COLUMN execution_started_at TIMESTAMP NULL AFTER executor_iam_user_id,
    ADD COLUMN executed_at TIMESTAMP NULL AFTER execution_started_at,
    ADD COLUMN executed_from_node VARCHAR(64) NULL AFTER executed_at,
    ADD COLUMN executed_to_node VARCHAR(64) NULL AFTER executed_from_node,
    ADD COLUMN execution_failure_reason VARCHAR(160) NULL AFTER executed_to_node;

CREATE INDEX idx_controlled_jump_execution ON ticket_controlled_jump_request (ticket_id, status, execution_started_at);
