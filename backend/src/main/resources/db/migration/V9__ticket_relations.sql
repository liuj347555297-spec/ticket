-- A controlled, audited relationship graph.  No hard deletes are exposed by the application.
CREATE TABLE ticket_relation (
    ticket_id VARCHAR(24) NOT NULL,
    related_ticket_id VARCHAR(24) NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id, related_ticket_id, relation_type),
    KEY idx_ticket_relation_related (related_ticket_id, created_at DESC),
    CONSTRAINT ck_ticket_relation_distinct CHECK (ticket_id <> related_ticket_id),
    CONSTRAINT fk_ticket_relation_source FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT fk_ticket_relation_target FOREIGN KEY (related_ticket_id) REFERENCES ticket(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
