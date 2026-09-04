-- Immutable release evidence for service-system routing metadata. V37 remains the current-state model.
CREATE TABLE service_system_registry_publication_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    system_code VARCHAR(64) NOT NULL,
    registry_version BIGINT NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_service_system_publication_revision (system_code, registry_version),
    KEY idx_service_system_publication_history (system_code, occurred_at),
    CONSTRAINT fk_service_system_publication_history_system FOREIGN KEY (system_code) REFERENCES service_system_registry(system_code),
    CONSTRAINT ck_service_system_publication_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_service_system_publication_action CHECK (action IN ('CREATED','UPDATED','PUBLISHED','RETIRED')),
    CONSTRAINT ck_service_system_publication_version CHECK (registry_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
