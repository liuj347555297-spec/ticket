-- The business projection keeps the exact Flowable definition that started the current
-- lifecycle instance. Existing rows remain readable as legacy history; new starts and reopen
-- operations always capture both fields before any ticket action is accepted.
ALTER TABLE ticket_workflow_instance
    ADD COLUMN process_definition_id VARCHAR(128) NULL AFTER primary_assignee_iam_user_id,
    ADD COLUMN process_definition_version INT NULL AFTER process_definition_id;
