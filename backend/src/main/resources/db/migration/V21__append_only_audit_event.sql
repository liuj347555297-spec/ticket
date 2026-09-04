-- V1 already created the original audit_event table.  This migration transforms that
-- table in place, preserving its historical rows while making newly written UUID event
-- IDs and the append-only event vocabulary available.  It intentionally does not create
-- a second table, so every V1–V20 database follows the same upgrade path.
ALTER TABLE audit_event
    MODIFY COLUMN id CHAR(36) NOT NULL,
    MODIFY COLUMN resource_type VARCHAR(96) NOT NULL,
    MODIFY COLUMN resource_id VARCHAR(160) NOT NULL,
    CHANGE COLUMN action action_code VARCHAR(128) NOT NULL,
    CHANGE COLUMN attributes attributes_json JSON NOT NULL,
    DROP INDEX idx_audit_event_resource,
    DROP INDEX idx_audit_event_actor,
    ADD KEY idx_audit_event_occurred_at (occurred_at),
    ADD KEY idx_audit_event_actor_time (actor_iam_user_id, occurred_at),
    ADD KEY idx_audit_event_resource_time (resource_type, resource_id, occurred_at),
    ADD KEY idx_audit_event_action_time (action_code, occurred_at);
