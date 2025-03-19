--liquibase formatted sql
--changeset Topias Heinonen:106-create-table-for-muutosilmoitus-attachment
--comment: Create table for muutosilmoitus attachment

CREATE TABLE muutosilmoituksen_liite
(
    id                 uuid                     NOT NULL PRIMARY KEY,
    muutosilmoitus_id  uuid                     NOT NULL,
    file_name          varchar                  NOT NULL,
    attachment_type    varchar                  NOT NULL,
    content_type       varchar                  NOT NULL,
    size               bigint                   NOT NULL,
    blob_location      varchar                  NOT NULL,
    created_by_user_id varchar                  NOT NULL,
    created_at         timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_liite_muutosilmoitus FOREIGN KEY (muutosilmoitus_id) REFERENCES muutosilmoitus (id) ON DELETE CASCADE
);

CREATE INDEX idx_liite_muutosilmoitus_id ON muutosilmoituksen_liite (muutosilmoitus_id);

COMMENT ON TABLE muutosilmoituksen_liite is 'Attachment metadata for muutosilmoitus.';
COMMENT ON COLUMN muutosilmoituksen_liite.muutosilmoitus_id is 'The muutosilmoitus this attachment belongs to.';
COMMENT ON COLUMN muutosilmoituksen_liite.file_name is 'The name of the attachment file.';
COMMENT ON COLUMN muutosilmoituksen_liite.attachment_type is 'The type of the attachment.';
COMMENT ON COLUMN muutosilmoituksen_liite.content_type is 'The content type of the attachment.';
COMMENT ON COLUMN muutosilmoituksen_liite.size is 'Size of the attachment in bytes.';
COMMENT ON COLUMN muutosilmoituksen_liite.blob_location is 'The location of the attachment in the blob storage.';
COMMENT ON COLUMN muutosilmoituksen_liite.created_by_user_id is 'The user who created this attachment.';
COMMENT ON COLUMN muutosilmoituksen_liite.created_at is 'Timestamp of when this row was initially created.';
