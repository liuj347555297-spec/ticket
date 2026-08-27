ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN source_ticket_version BIGINT NULL AFTER source_node,
    ADD COLUMN source_workflow_version BIGINT NULL AFTER source_ticket_version;
