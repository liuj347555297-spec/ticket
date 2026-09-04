-- Service-system registry: platform-owned support/routing metadata, not a writable CMDB replica.
CREATE TABLE service_system_registry (
    system_code VARCHAR(64) NOT NULL,
    system_name VARCHAR(200) NOT NULL,
    ci_id VARCHAR(128) NULL,
    owner_iam_user_id VARCHAR(128) NULL,
    owning_organization_id VARCHAR(128) NOT NULL,
    lifecycle_status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    change_reason VARCHAR(500) NOT NULL,
    created_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (system_code),
    UNIQUE KEY uk_service_system_name_org (system_name, owning_organization_id),
    KEY idx_service_system_status_org (lifecycle_status, owning_organization_id, system_name),
    CONSTRAINT fk_service_system_ci FOREIGN KEY (ci_id) REFERENCES cmdb_configuration_item_projection(ci_id),
    CONSTRAINT ck_service_system_status CHECK (lifecycle_status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_service_system_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_system_module (
    system_code VARCHAR(64) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(200) NOT NULL,
    module_path VARCHAR(500) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_code, module_code),
    UNIQUE KEY uk_service_system_module_name (system_code, module_name),
    KEY idx_service_system_module_active (system_code, active, sort_order),
    CONSTRAINT fk_service_system_module_system FOREIGN KEY (system_code) REFERENCES service_system_registry(system_code),
    CONSTRAINT ck_service_system_module_sort CHECK (sort_order >= 0),
    CONSTRAINT ck_service_system_module_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_system_catalog_mapping (
    system_code VARCHAR(64) NOT NULL,
    service_catalog_item_id VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_code, service_catalog_item_id),
    KEY idx_service_system_catalog_active (system_code, active, is_default),
    CONSTRAINT fk_service_system_catalog_system FOREIGN KEY (system_code) REFERENCES service_system_registry(system_code),
    CONSTRAINT fk_service_system_catalog_item FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_item(id),
    CONSTRAINT ck_service_system_catalog_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_system_module_catalog_mapping (
    system_code VARCHAR(64) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    service_catalog_item_id VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_by_iam_user_id VARCHAR(128) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_code, module_code, service_catalog_item_id),
    KEY idx_service_system_module_catalog_active (system_code, module_code, active, is_default),
    CONSTRAINT fk_service_system_module_catalog_module FOREIGN KEY (system_code, module_code) REFERENCES service_system_module(system_code, module_code),
    CONSTRAINT fk_service_system_module_catalog_item FOREIGN KEY (service_catalog_item_id) REFERENCES service_catalog_item(id),
    CONSTRAINT ck_service_system_module_catalog_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Immutable ticket-side display/routing evidence; later registry changes never revise history.
CREATE TABLE ticket_service_system_snapshot (
    ticket_id VARCHAR(24) NOT NULL,
    system_code VARCHAR(64) NOT NULL,
    system_name VARCHAR(200) NOT NULL,
    module_code VARCHAR(64) NULL,
    module_name VARCHAR(200) NULL,
    service_catalog_item_id VARCHAR(64) NOT NULL,
    registry_version BIGINT NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    PRIMARY KEY (ticket_id),
    KEY idx_ticket_service_system_catalog (system_code, service_catalog_item_id, captured_at),
    CONSTRAINT fk_ticket_service_system_snapshot_ticket FOREIGN KEY (ticket_id) REFERENCES ticket(id),
    CONSTRAINT ck_ticket_service_system_snapshot_version CHECK (registry_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
