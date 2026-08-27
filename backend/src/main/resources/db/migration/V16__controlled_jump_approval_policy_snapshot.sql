-- Freeze the policy that governed each approval request. New definitions or role mappings must
-- never silently rewrite an in-flight or historical approval decision.
ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approval_process_key VARCHAR(128) NULL AFTER approval_engine_instance_id,
    ADD COLUMN approval_process_definition_id VARCHAR(128) NULL AFTER approval_process_key,
    ADD COLUMN approval_process_version INT UNSIGNED NULL AFTER approval_process_definition_id,
    ADD COLUMN approval_candidate_roles_json JSON NULL AFTER approval_process_version,
    ADD COLUMN approval_decision_mode VARCHAR(32) NULL AFTER approval_candidate_roles_json,
    ADD COLUMN approval_timeout_policy_version VARCHAR(128) NULL AFTER approval_decision_mode,
    ADD COLUMN approval_escalation_policy_version VARCHAR(128) NULL AFTER approval_timeout_policy_version,
    ADD COLUMN approval_policy_captured_at DATETIME(6) NULL AFTER approval_escalation_policy_version;

-- Historical records cannot safely claim a concrete Flowable definition id. Mark them explicitly
-- so application services can fail closed instead of silently applying the current definition.
UPDATE ticket_controlled_jump_request
   SET approval_process_key = 'servicehubControlledJumpApproval',
       approval_process_definition_id = 'LEGACY_UNRECORDED',
       approval_process_version = 0,
       approval_candidate_roles_json = JSON_ARRAY('ROLE_SERVICE_MANAGER', 'ROLE_PLATFORM_ADMIN'),
       approval_decision_mode = 'ANY_ONE',
       approval_timeout_policy_version = 'NONE',
       approval_escalation_policy_version = 'NONE',
       approval_policy_captured_at = created_at
 WHERE approval_process_key IS NULL;

ALTER TABLE ticket_controlled_jump_request
    MODIFY COLUMN approval_process_key VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_process_definition_id VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_process_version INT UNSIGNED NOT NULL,
    MODIFY COLUMN approval_candidate_roles_json JSON NOT NULL,
    MODIFY COLUMN approval_decision_mode VARCHAR(32) NOT NULL,
    MODIFY COLUMN approval_timeout_policy_version VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_escalation_policy_version VARCHAR(128) NOT NULL,
    MODIFY COLUMN approval_policy_captured_at DATETIME(6) NOT NULL;
