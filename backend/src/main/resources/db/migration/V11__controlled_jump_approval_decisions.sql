ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approver_iam_user_id VARCHAR(128) NULL AFTER status,
    ADD COLUMN decision_reason VARCHAR(1000) NULL AFTER approver_iam_user_id,
    ADD COLUMN decided_at DATETIME(6) NULL AFTER decision_reason;
