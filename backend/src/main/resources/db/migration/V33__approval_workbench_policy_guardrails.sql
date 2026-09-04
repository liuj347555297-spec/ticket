-- A single active policy per action/scope prevents an ambiguous "latest wins" approval route.
-- NULL keeps DRAFT and RETIRED revisions outside the uniqueness rule.
ALTER TABLE lifecycle_approval_policy
    ADD COLUMN published_scope_key VARCHAR(320)
        GENERATED ALWAYS AS (
            CASE WHEN lifecycle_status = 'PUBLISHED'
                 THEN CONCAT(action_code, '|', IFNULL(service_catalog_item_id, '*'), '|', IFNULL(ticket_priority, '*'))
                 ELSE NULL END
        ) STORED,
    ADD UNIQUE KEY uk_lifecycle_approval_policy_published_scope (published_scope_key);
