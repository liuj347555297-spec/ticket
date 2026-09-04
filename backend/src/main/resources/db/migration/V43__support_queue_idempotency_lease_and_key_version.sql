ALTER TABLE support_queue_command_idempotency
 ADD COLUMN lease_owner VARCHAR(128) NULL AFTER version,
 ADD COLUMN lease_expires_at DATETIME(6) NULL AFTER lease_owner,
 ADD COLUMN attempt_count INT NOT NULL DEFAULT 1 AFTER lease_expires_at,
 ADD COLUMN key_version VARCHAR(64) NULL AFTER attempt_count,
 ADD COLUMN result_resource_type VARCHAR(64) NULL AFTER key_version,
 ADD COLUMN result_resource_id VARCHAR(128) NULL AFTER result_resource_type,
 ADD COLUMN heartbeat_at DATETIME(6) NULL AFTER result_resource_id;

ALTER TABLE support_queue_command_idempotency DROP CONSTRAINT ck_support_queue_command_status;
ALTER TABLE support_queue_command_idempotency ADD CONSTRAINT ck_support_queue_command_status CHECK(status IN('IN_PROGRESS','RECONCILIATION_REQUIRED','SUCCEEDED','FAILED_RETRYABLE','FAILED_FINAL'));
ALTER TABLE support_queue_command_idempotency ADD CONSTRAINT ck_support_queue_command_attempt CHECK(attempt_count>=1);
CREATE INDEX idx_support_queue_command_lease ON support_queue_command_idempotency(status,lease_expires_at,version);
CREATE INDEX idx_support_queue_command_result ON support_queue_command_idempotency(result_resource_type,result_resource_id);
CREATE INDEX idx_support_queue_command_key_retention ON support_queue_command_idempotency(key_version,status,expires_at);
