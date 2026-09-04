-- Ticket submission is bound to the actual published schema revision, so future form changes cannot rewrite history.
ALTER TABLE ticket ADD COLUMN service_catalog_form_version INT NOT NULL DEFAULT 1 AFTER service_catalog_item_name;
ALTER TABLE ticket ADD CONSTRAINT ck_ticket_service_catalog_form_version CHECK (service_catalog_form_version >= 1);
