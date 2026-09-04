ALTER TABLE support_queue_command_idempotency DROP CONSTRAINT ck_support_queue_command_status;

UPDATE support_queue_command_idempotency
   SET status='LEGACY_RESULT_ONLY',response_summary=NULL,lease_owner=NULL,lease_expires_at=NULL,heartbeat_at=NULL
 WHERE key_version IS NULL AND status='SUCCEEDED' AND result_resource_type IS NOT NULL AND result_resource_id IS NOT NULL;
UPDATE support_queue_command_idempotency
   SET status='RECONCILIATION_REQUIRED',response_summary=NULL,lease_owner=NULL,lease_expires_at=NULL,heartbeat_at=NULL,error_code='LEGACY_RESULT_UNPROVEN'
 WHERE key_version IS NULL AND status IN('SUCCEEDED','IN_PROGRESS','RECONCILIATION_REQUIRED');
UPDATE support_queue_command_idempotency
   SET status='FAILED_FINAL',response_summary=NULL,lease_owner=NULL,lease_expires_at=NULL,heartbeat_at=NULL
 WHERE key_version IS NULL AND status IN('FAILED_RETRYABLE','FAILED_FINAL');
UPDATE support_queue_command_idempotency SET key_version='LEGACY_UNVERIFIABLE' WHERE key_version IS NULL;

ALTER TABLE support_queue_command_idempotency MODIFY key_version VARCHAR(64) NOT NULL;
ALTER TABLE support_queue_command_idempotency ADD CONSTRAINT ck_support_queue_command_status CHECK(status IN('IN_PROGRESS','RECONCILIATION_REQUIRED','LEGACY_RESULT_ONLY','SUCCEEDED','FAILED_RETRYABLE','FAILED_FINAL'));
ALTER TABLE support_queue_command_idempotency ADD CONSTRAINT ck_support_queue_legacy_state CHECK(key_version<>'LEGACY_UNVERIFIABLE' OR status IN('RECONCILIATION_REQUIRED','LEGACY_RESULT_ONLY','FAILED_FINAL'));

CREATE TABLE support_queue_idempotency_reconciliation_request (
 id VARCHAR(72) NOT NULL,target_actor_iam_user_id VARCHAR(128) NOT NULL,idempotency_key CHAR(36) NOT NULL,command_version BIGINT NOT NULL,decision VARCHAR(32) NOT NULL,result_resource_type VARCHAR(64) NULL,result_resource_id VARCHAR(128) NULL,reason VARCHAR(500) NOT NULL,status VARCHAR(16) NOT NULL,requested_by_iam_user_id VARCHAR(128) NOT NULL,approved_by_iam_user_id VARCHAR(128) NULL,version BIGINT NOT NULL DEFAULT 0,created_at DATETIME(6) NOT NULL,decided_at DATETIME(6) NULL,
 PRIMARY KEY(id),UNIQUE KEY uk_support_queue_reconciliation_pending(target_actor_iam_user_id,idempotency_key,status),KEY idx_support_queue_reconciliation_status(status,created_at),
 CONSTRAINT fk_support_queue_reconciliation_command FOREIGN KEY(target_actor_iam_user_id,idempotency_key) REFERENCES support_queue_command_idempotency(actor_iam_user_id,idempotency_key),
 CONSTRAINT ck_support_queue_reconciliation_decision CHECK(decision IN('LEGACY_RESULT_ONLY','FAILED_FINAL')),CONSTRAINT ck_support_queue_reconciliation_status CHECK(status IN('PENDING','APPROVED','REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
