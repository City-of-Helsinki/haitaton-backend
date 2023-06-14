--liquibase formatted sql
--changeset Topias Heinonen:038-add-attachment-content-tables
--comment: Add separate tables for storing attachment file content

CREATE TABLE hanke_attachment_content
(
    attachment_id uuid PRIMARY KEY NOT NULL,
    content       bytea            NOT NULL,
    CONSTRAINT fk_hanke_attachment
        FOREIGN KEY (attachment_id)
            REFERENCES hanke_attachment (id) ON DELETE CASCADE
);

COMMENT ON TABLE hanke_attachment_content IS 'Separate table for holding just the binary content of the attachment file. Separated so JPA does not load the binaries unexpectedly.';
COMMENT ON COLUMN hanke_attachment_content.attachment_id IS 'ID of the attachment this binary data belongs to. Also the primary key, since this is a strict one-to-one relationship.';
COMMENT ON COLUMN hanke_attachment_content.content IS 'The file content as a binary BLOB.';

INSERT INTO hanke_attachment_content
SELECT id, content
FROM hanke_attachment;

ALTER TABLE hanke_attachment
    DROP COLUMN content;

CREATE TABLE application_attachment_content
(
    attachment_id uuid PRIMARY KEY NOT NULL,
    content       bytea            NOT NULL,
    CONSTRAINT fk_application_attachment
        FOREIGN KEY (attachment_id)
            REFERENCES application_attachment (id) ON DELETE CASCADE
);

COMMENT ON TABLE application_attachment_content IS 'Separate table for holding just the binary content of the attachment file. Separated so JPA does not load the binaries unexpectedly.';
COMMENT ON COLUMN application_attachment_content.attachment_id IS 'ID of the attachment this binary data belongs to. Also the primary key, since this is a strict one-to-one relationship.';
COMMENT ON COLUMN application_attachment_content.content IS 'The file content as a binary BLOB.';


INSERT INTO application_attachment_content
SELECT id, content
FROM application_attachment;

ALTER TABLE application_attachment
    DROP COLUMN content;
