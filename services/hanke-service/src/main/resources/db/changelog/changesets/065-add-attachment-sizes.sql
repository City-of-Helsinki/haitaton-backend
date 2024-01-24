--liquibase formatted sql
--changeset Teemu Hiltunen:065-add-attachment-sizes
--comment: Add column size to application_attachment and hanke_attachment tables

-- Application attachment size
ALTER TABLE application_attachment
    ADD COLUMN size BIGINT NOT NULL default 0;

UPDATE application_attachment
SET size = length(content)
FROM application_attachment_content
WHERE application_attachment.id = application_attachment_content.attachment_id;

ALTER TABLE application_attachment
    ALTER COLUMN size DROP DEFAULT;

COMMENT ON COLUMN application_attachment.size IS 'Size of the attachment in bytes';

-- Hanke attachment size
ALTER TABLE hanke_attachment
    ADD COLUMN size BIGINT NOT NULL default 0;

-- Cannot initialize hanke_attachment.size because Hanke attachment content is already moved into Azure Blob Storage

ALTER TABLE hanke_attachment
    ALTER COLUMN size DROP DEFAULT;

COMMENT ON COLUMN hanke_attachment.size IS 'Size of the attachment in bytes';
