--liquibase formatted sql
--changeset Topias Heinonen:078-create-paatos-table
--comment: Create a table for paatos, a single decision document that might be current or replaced.

CREATE TABLE paatos
(

    id             uuid                     NOT NULL PRIMARY KEY,
    application_id bigint                   NOT NULL,
    hakemustunnus  varchar                  NOT NULL,
    tyyppi         varchar                  NOT NULL,
    tila           varchar                  NOT NULL,
    nimi           varchar                  NOT NULL,
    alkupaiva      date                     NOT NULL,
    loppupaiva     date                     NOT NULL,
    blob_location  varchar                  NOT NULL,
    size           integer                  NOT NULL,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_paatos_applications
        FOREIGN KEY (application_id) references applications (id) ON DELETE CASCADE

);

CREATE UNIQUE INDEX uk_paatos_hakemustunnus_tyyppi ON paatos (hakemustunnus, tyyppi);
CREATE INDEX idx_paatos_applicationid on paatos (application_id);
CREATE INDEX idx_paatos_applicationid_tila on paatos (application_id, tila);

COMMENT ON COLUMN paatos.application_id IS 'Id of the application is decision is made for.';
COMMENT ON COLUMN paatos.hakemustunnus IS 'The identifier of this decision.';
COMMENT ON COLUMN paatos.tyyppi IS 'The type of the document. One of: PAATOS, TOIMINNALLINEN_KUNTO, TYO_VALMIS';
COMMENT ON COLUMN paatos.tila IS 'The status of the document. One of: NYKYINEN, KORVATTU';
COMMENT ON COLUMN paatos.nimi IS 'The name the application has when the document is created.';
COMMENT ON COLUMN paatos.alkupaiva IS 'The start date the application has when the document is created.';
COMMENT ON COLUMN paatos.loppupaiva IS 'The end date the application has when the document is created.';
COMMENT ON COLUMN paatos.blob_location IS 'The location of the attachment data in Azure Blob.';
COMMENT ON COLUMN paatos.size IS 'Size of the attachment data.';
COMMENT ON COLUMN paatos.created_at IS 'Creation time of this row.';
