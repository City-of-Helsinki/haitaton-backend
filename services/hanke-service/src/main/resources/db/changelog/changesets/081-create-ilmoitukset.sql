--liquibase formatted sql
--changeset Topias Heinonen:081-create-ilmoitukset
--comment: Create a table for operational condition and work finished reports.

CREATE TABLE ilmoitus
(
    id             UUID PRIMARY KEY         NOT NULL,
    application_id BIGINT                   NOT NULL,
    type           TEXT                     NOT NULL, -- TOIMINNALLINEN_KUNTO tai TYO_VALMIS
    hakemustunnus  TEXT                     NOT NULL,
    date_reported  DATE                     NOT NULL,
    created_at     timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ilmoitukset_applications FOREIGN KEY (application_id) references applications (id) ON DELETE CASCADE
);

COMMENT ON TABLE ilmoitus IS 'Operational condition and work finished reports.';
COMMENT ON COLUMN ilmoitus.id IS 'Unique key for this row.';
COMMENT ON COLUMN ilmoitus.application_id IS 'Application the report is for.';
COMMENT ON COLUMN ilmoitus.type IS 'Whether this is an operational condition (TOIMINNALLINEN KUNTO) or work finished (TYO_VALMIS) report.';
COMMENT ON COLUMN ilmoitus.hakemustunnus IS 'The application identifier the application had when the report was made.';
COMMENT ON COLUMN ilmoitus.date_reported IS 'Date when the work was in operational condition or finished.';
COMMENT ON COLUMN ilmoitus.created_at IS 'Date and time when the report was made.';
