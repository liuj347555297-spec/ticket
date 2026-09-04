-- Forward-only indexes for authorization-first ticket keyset queries. Queue scopes remain
-- fail-closed until a durable queue snapshot schema is introduced in a later migration.
CREATE INDEX idx_ticket_created_cursor ON ticket (created_at DESC, id DESC);
CREATE INDEX idx_ticket_requester_cursor ON ticket (requester_iam_user_id, created_at DESC, id DESC);
CREATE INDEX idx_ticket_organization_cursor ON ticket (requester_organization_id, created_at DESC, id DESC);
CREATE INDEX idx_ticket_catalog_cursor ON ticket (service_catalog_item_id, created_at DESC, id DESC);
CREATE INDEX idx_ticket_org_catalog_cursor ON ticket (requester_organization_id, service_catalog_item_id, created_at DESC, id DESC);

CREATE INDEX idx_ticket_workflow_participant_user_active ON ticket_workflow_participant (iam_user_id, active, ticket_id);
CREATE INDEX idx_ticket_workflow_task_assignee_active ON ticket_workflow_task (assignee_iam_user_id, status, ticket_id);
CREATE INDEX idx_ticket_workflow_task_candidate_user_active ON ticket_workflow_task (candidate_iam_user_id, status, ticket_id);
CREATE INDEX idx_ticket_workflow_task_candidate_role_active ON ticket_workflow_task (candidate_role, status, ticket_id);
CREATE INDEX idx_notification_recipient_ticket_unread ON notification (recipient_iam_user_id, read_at, ticket_id);
