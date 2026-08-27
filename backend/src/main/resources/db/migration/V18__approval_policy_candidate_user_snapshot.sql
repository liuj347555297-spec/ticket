ALTER TABLE ticket_controlled_jump_request
    ADD COLUMN approval_candidate_iam_user_ids_json JSON NULL AFTER approval_candidate_roles_json;

UPDATE ticket_controlled_jump_request
   SET approval_candidate_iam_user_ids_json = JSON_ARRAY()
 WHERE approval_candidate_iam_user_ids_json IS NULL;

ALTER TABLE ticket_controlled_jump_request
    MODIFY COLUMN approval_candidate_iam_user_ids_json JSON NOT NULL;
