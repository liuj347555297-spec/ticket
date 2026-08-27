-- Application events are append-only evidence.  The application database user receives no
-- UPDATE/DELETE grant on this table in production; retention/export belongs to audited DBA jobs.
CREATE TABLE audit_event (
    id CHAR(36) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    resource_type VARCHAR(96) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    attributes_json JSON NOT NULL,
    PRIMARY KEY (id),
    KEY idx_audit_event_occurred_at (occurred_at),
    KEY idx_audit_event_actor_time (actor_iam_user_id, occurred_at),
    KEY idx_audit_event_resource_time (resource_type, resource_id, occurred_at),
    KEY idx_audit_event_action_time (action_code, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
