-- Published schema snapshots are append-only, allowing rollback to create a new draft without rewriting history.
CREATE TABLE service_catalog_form_configuration_history LIKE service_catalog_form_configuration;
ALTER TABLE service_catalog_form_configuration_history DROP PRIMARY KEY;
ALTER TABLE service_catalog_form_configuration_history DROP INDEX uk_service_catalog_form_configuration_code;
ALTER TABLE service_catalog_form_configuration_history ADD PRIMARY KEY (code, form_version);
