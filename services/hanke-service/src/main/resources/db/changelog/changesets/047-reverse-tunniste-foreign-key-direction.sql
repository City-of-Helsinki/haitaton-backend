--liquibase formatted sql
--changeset Topias Heinonen:047-reverse-tunniste-foreign-key-direction
--comment: Add RESEND_INVITATION permission code

ALTER TABLE kayttaja_tunniste
    ADD COLUMN hanke_kayttaja_id uuid;

UPDATE kayttaja_tunniste
SET hanke_kayttaja_id = (SELECT hanke_kayttaja.id
                         from hanke_kayttaja
                         WHERE hanke_kayttaja.tunniste_id = kayttaja_tunniste.id);

-- The ones where hanke_kayttaja_id is null are orphans,
-- i.e. ones where there's no hanke_kayttaja that links to them.
DELETE
FROM kayttaja_tunniste
WHERE hanke_kayttaja_id IS NULL;

ALTER TABLE hanke_kayttaja
    DROP CONSTRAINT IF EXISTS fk_hankekayttaja_kayttajatunniste,
    DROP COLUMN IF EXISTS tunniste_id;

ALTER TABLE kayttaja_tunniste
    ALTER COLUMN hanke_kayttaja_id SET NOT NULL,
    ADD CONSTRAINT fk_kayttajatunniste_hankekayttaja FOREIGN KEY (hanke_kayttaja_id) REFERENCES hanke_kayttaja (id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_hanke_kayttaja_id UNIQUE (hanke_kayttaja_id);

COMMENT ON COLUMN kayttaja_tunniste.hanke_kayttaja_id IS 'The kayttaja this tunniste belongs to. This field is unique, so there can be only one tunniste for each kayttaja.';
