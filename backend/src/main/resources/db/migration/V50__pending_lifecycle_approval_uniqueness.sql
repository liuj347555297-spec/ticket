-- A current source state can have only one live request for the same lifecycle action. Historical
-- and terminal requests remain append-only because their generated key is NULL.
ALTER TABLE ticket_lifecycle_action_approval_request
    ADD COLUMN active_source_action_key VARCHAR(160)
        GENERATED ALWAYS AS (
            CASE WHEN status IN ('PENDING_APPROVAL','EXPIRING')
                 THEN CONCAT(ticket_id,'|',action_code,'|',source_ticket_version,'|',source_workflow_version)
                 ELSE NULL END
        ) STORED,
    ADD UNIQUE KEY uk_lifecycle_approval_active_source_action (active_source_action_key);
