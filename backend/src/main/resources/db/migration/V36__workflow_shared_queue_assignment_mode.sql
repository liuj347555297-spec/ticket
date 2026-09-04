ALTER TABLE workflow_node_assignment_policy DROP CONSTRAINT ck_workflow_node_assignment_mode;
ALTER TABLE workflow_node_assignment_policy ADD CONSTRAINT ck_workflow_node_assignment_mode CHECK (assignment_mode IN ('SYSTEM_RANDOM','PREVIOUS_HANDLER_SELECTS','SHARED_QUEUE'));
ALTER TABLE ticket_workflow_node_assignment_snapshot DROP CONSTRAINT ck_ticket_workflow_assignment_mode;
ALTER TABLE ticket_workflow_node_assignment_snapshot ADD CONSTRAINT ck_ticket_workflow_assignment_mode CHECK (assignment_mode IN ('SYSTEM_RANDOM','PREVIOUS_HANDLER_SELECTS','SHARED_QUEUE'));
