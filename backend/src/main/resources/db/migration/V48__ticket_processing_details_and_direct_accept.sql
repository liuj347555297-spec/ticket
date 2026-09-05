CREATE TABLE ticket_processing_details (
    ticket_id VARCHAR(32) PRIMARY KEY,
    event_source VARCHAR(32) NULL,
    proposing_organization VARCHAR(160) NULL,
    on_site_support_required BOOLEAN NULL,
    cause_category VARCHAR(40) NULL,
    processing_description TEXT NULL,
    resolution_description TEXT NULL,
    third_party_handled BOOLEAN NULL,
    current_progress TEXT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ticket_processing_details_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT ck_ticket_processing_details_version CHECK (version >= 1),
    CONSTRAINT ck_ticket_processing_details_event_source CHECK (event_source IS NULL OR event_source IN ('PHONE','EMAIL','MONITORING_ALERT','ON_SITE_FEEDBACK','OTHER')),
    CONSTRAINT ck_ticket_processing_details_cause CHECK (cause_category IS NULL OR cause_category IN ('HARDWARE','SOFTWARE_DEFECT','CONFIGURATION','NETWORK','ACCESS_CONTROL','DATA','USER_OPERATION','EXTERNAL_DEPENDENCY','UNDER_INVESTIGATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The former blanket fallbacks made routine acceptance, resolution and closure approval requests.
-- Existing explicitly scoped published policies remain active and continue to govern matching tickets.
UPDATE lifecycle_approval_policy
   SET lifecycle_status='RETIRED', version=version+1, updated_at=UTC_TIMESTAMP(6)
 WHERE id IN ('00000000-0000-4000-8000-000000000037','00000000-0000-4000-8000-000000000038','00000000-0000-4000-8000-000000000039')
   AND action_code IN ('ACCEPT','RESOLVE','CLOSE') AND lifecycle_status='PUBLISHED';
