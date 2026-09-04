-- Keep the original V28 approval history immutable while extending the same isolated
-- Flowable approval aggregate to four additional lifecycle actions.  ASSIGN needs a
-- server-frozen target; all other approved actions deliberately keep this column NULL.
ALTER TABLE ticket_lifecycle_action_approval_request
    ADD COLUMN target_iam_user_id VARCHAR(128) NULL AFTER reason;

ALTER TABLE ticket_lifecycle_action_approval_request
    DROP CHECK ck_lifecycle_action_approval_action;

ALTER TABLE ticket_lifecycle_action_approval_request
    ADD CONSTRAINT ck_lifecycle_action_approval_action
        CHECK (action_code IN ('HOLD', 'ESCALATE', 'CANCEL', 'REOPEN', 'ASSIGN', 'ACCEPT', 'RESOLVE', 'CLOSE'));

ALTER TABLE ticket_lifecycle_action_approval_request
    ADD CONSTRAINT ck_lifecycle_action_approval_target
        CHECK ((action_code = 'ASSIGN' AND target_iam_user_id IS NOT NULL)
            OR (action_code <> 'ASSIGN' AND target_iam_user_id IS NULL));
