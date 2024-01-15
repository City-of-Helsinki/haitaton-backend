--liquibase formatted sql
--changeset Topias Heinonen:067-create-hankeyhteyshenkilo-table
--comment: Create a table for hankeyhteyshenkilo, a link table between hankekayttaja and hankeyhteystieto

CREATE TABLE hankeyhteyshenkilo
(
    id                  uuid                     NOT NULL PRIMARY KEY,
    hankekayttaja_id    uuid                     NOT NULL,
    hankeyhteystieto_id integer                  NOT NULL,
    created_at          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hankeyhteyshenkilo_hankekayttaja
        FOREIGN KEY (hankekayttaja_id)
            REFERENCES hankekayttaja (id) ON DELETE CASCADE,
    CONSTRAINT fk_hankeyhteyshenkilo_hankeyhteystieto
        FOREIGN KEY (hankeyhteystieto_id)
            REFERENCES hankeyhteystieto (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uk_hankekayttaja_hankeyhteystieto ON hankeyhteyshenkilo (hankekayttaja_id, hankeyhteystieto_id);
CREATE INDEX idx_hankekayttaja ON hankeyhteyshenkilo (hankekayttaja_id);
CREATE INDEX idx_hankeyhteystieto ON hankeyhteyshenkilo (hankeyhteystieto_id);

COMMENT ON TABLE hankeyhteyshenkilo IS 'Table for linking the hanke users and hanke customers as contact persons.';
COMMENT ON COLUMN hankeyhteyshenkilo.hankekayttaja_id IS 'The kayttaja who this contact is.';
COMMENT ON COLUMN hankeyhteyshenkilo.hankeyhteystieto_id IS 'The customer this contact is represents.';
COMMENT ON COLUMN hankeyhteyshenkilo.created_at IS 'The time this row was originally created.';

ALTER TABLE hankeyhteystieto
DROP COLUMN yhteyshenkilot;

UPDATE hanke SET status='DRAFT' WHERE status = 'PUBLIC';
