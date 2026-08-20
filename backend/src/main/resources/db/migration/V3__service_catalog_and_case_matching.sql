-- Service catalog is configuration data managed through a later, separately-authorized console.
-- Requesters may read only PUBLISHED records; no browser request is allowed to supply a form definition.
CREATE TABLE service_catalog_item (
    id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    supported_ticket_types JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_service_catalog_item_status (publication_status, name),
    CONSTRAINT ck_service_catalog_item_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_service_catalog_item_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_dictionary (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (code),
    CONSTRAINT ck_service_catalog_dictionary_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_service_catalog_dictionary_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_dictionary_option (
    dictionary_code VARCHAR(64) NOT NULL,
    option_code VARCHAR(128) NOT NULL,
    option_label VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (dictionary_code, option_code),
    CONSTRAINT fk_catalog_dictionary_option_dictionary FOREIGN KEY (dictionary_code)
        REFERENCES service_catalog_dictionary (code),
    CONSTRAINT ck_service_catalog_dictionary_option_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE service_catalog_form_field (
    catalog_item_id VARCHAR(64) NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_label VARCHAR(200) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    max_length INT NULL,
    dictionary_code VARCHAR(64) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (catalog_item_id, field_code),
    KEY idx_service_catalog_form_field_dictionary (dictionary_code),
    CONSTRAINT fk_catalog_form_field_item FOREIGN KEY (catalog_item_id) REFERENCES service_catalog_item (id),
    CONSTRAINT fk_catalog_form_field_dictionary FOREIGN KEY (dictionary_code) REFERENCES service_catalog_dictionary (code),
    CONSTRAINT ck_service_catalog_form_field_type CHECK (field_type IN ('TEXT', 'SINGLE_SELECT', 'MULTI_SELECT', 'CI_ID')),
    CONSTRAINT ck_service_catalog_form_field_max_length CHECK (max_length IS NULL OR max_length BETWEEN 1 AND 4000),
    CONSTRAINT ck_service_catalog_form_field_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Standard labels are centrally governed. Free labels remain restricted by the API's #tag grammar.
CREATE TABLE service_catalog_tag (
    tag_name VARCHAR(51) NOT NULL,
    tag_label VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (tag_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_case (
    id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    resolution_summary VARCHAR(2000) NOT NULL,
    publication_status VARCHAR(16) NOT NULL,
    published_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_knowledge_case_status (publication_status, updated_at DESC),
    CONSTRAINT ck_knowledge_case_status CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- One rule is an AND group. A matching rule can contribute one candidate and never changes ticket state.
CREATE TABLE knowledge_case_match_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    knowledge_case_id VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    catalog_item_id VARCHAR(64) NULL,
    configuration_item_id VARCHAR(128) NULL,
    field_code VARCHAR(64) NULL,
    field_value VARCHAR(4000) NULL,
    tag_name VARCHAR(51) NULL,
    tag_kind VARCHAR(16) NULL,
    error_code VARCHAR(128) NULL,
    keyword VARCHAR(200) NULL,
    score INT NOT NULL DEFAULT 50,
    PRIMARY KEY (id),
    KEY idx_case_match_rule_case (knowledge_case_id, enabled),
    KEY idx_case_match_rule_catalog (catalog_item_id, enabled),
    CONSTRAINT fk_case_match_rule_case FOREIGN KEY (knowledge_case_id) REFERENCES knowledge_case (id),
    CONSTRAINT fk_case_match_rule_catalog FOREIGN KEY (catalog_item_id) REFERENCES service_catalog_item (id),
    CONSTRAINT ck_case_match_rule_tag_kind CHECK (tag_kind IS NULL OR tag_kind IN ('STANDARD', 'FREE')),
    CONSTRAINT ck_case_match_rule_score CHECK (score BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- The request snapshot deliberately stores matching criteria, not browser/session credentials or full free-text descriptions.
CREATE TABLE knowledge_case_match_record (
    id CHAR(36) NOT NULL,
    actor_iam_user_id VARCHAR(128) NOT NULL,
    catalog_item_id VARCHAR(64) NOT NULL,
    criteria_hash CHAR(64) NOT NULL,
    matched_case_ids JSON NOT NULL,
    matched_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_case_match_record_actor_time (actor_iam_user_id, matched_at DESC),
    KEY idx_case_match_record_catalog_time (catalog_item_id, matched_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
