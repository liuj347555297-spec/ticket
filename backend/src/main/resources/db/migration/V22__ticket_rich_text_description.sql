-- Rich-text is stored only after server-side allow-list sanitization. Existing tickets remain plain text.
ALTER TABLE ticket
    ADD COLUMN description_format VARCHAR(16) NOT NULL DEFAULT 'PLAIN_TEXT' AFTER description,
    ADD COLUMN description_html MEDIUMTEXT NULL AFTER description_format;
