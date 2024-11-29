--liquibase formatted sql
--changeset Teemu Hiltunen:087-create-table-for-taydennys-attachment
--comment: Create table for taydennys attachment

CREATE TABLE taydennys_liite
(
    id                 uuid                     NOT NULL PRIMARY KEY,
    taydennys_id       uuid                     NOT NULL,
    file_name          varchar                  NOT NULL,
    attachment_type    varchar                  NOT NULL,
    content_type       varchar                  NOT NULL,
    size               bigint                   NOT NULL,
    blob_location      varchar                  NOT NULL,
    created_by_user_id varchar                  NOT NULL,
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_liite_taydennys FOREIGN KEY (taydennys_id) REFERENCES taydennys (id) ON DELETE CASCADE
);

CREATE INDEX idx_liite_taydennys_id ON taydennys_liite (taydennys_id);

COMMENT ON TABLE taydennys_liite is 'Attachment metadata for täydennys.';
COMMENT ON COLUMN taydennys_liite.taydennys_id is 'The täydennys this attachment belongs to.';
COMMENT ON COLUMN taydennys_liite.file_name is 'The name of the attachment file.';
COMMENT ON COLUMN taydennys_liite.attachment_type is 'The type of the attachment.';
COMMENT ON COLUMN taydennys_liite.content_type is 'The content type of the attachment.';
COMMENT ON COLUMN taydennys_liite.size is 'Size of the attachment in bytes.';
COMMENT ON COLUMN taydennys_liite.blob_location is 'The location of the attachment in the blob storage.';
COMMENT ON COLUMN taydennys_liite.created_by_user_id is 'The user who created this attachment.';
COMMENT ON COLUMN taydennys_liite.created_at is 'Timestamp of when this row was initially created.';
