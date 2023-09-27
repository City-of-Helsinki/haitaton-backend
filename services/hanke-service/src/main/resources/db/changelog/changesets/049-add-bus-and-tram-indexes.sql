--liquibase formatted sql
--changeset Topias Heinonen:049-add-bus-and-tram-indexes
--comment: Replace joukkoliikenneindeksi with separate bus and tram indexes. Make the indexes NOT NULL.

ALTER TABLE tormaystarkastelutulos
    ADD COLUMN linjaauto   DOUBLE PRECISION,
    ADD COLUMN raitiovaunu DOUBLE PRECISION;

-- There are no rows in production, so copy the old index to both new indexes to have some values there.
-- New indexes can be calculated by doing any update on the hanke.
UPDATE tormaystarkastelutulos
SET (linjaauto, raitiovaunu) = (joukkoliikenne, joukkoliikenne);

ALTER TABLE tormaystarkastelutulos
    DROP COLUMN joukkoliikenne;

-- These are not nullable in the entity, so the DB should match.
ALTER TABLE tormaystarkastelutulos
    ALTER COLUMN perus SET NOT NULL,
    ALTER COLUMN pyoraily SET NOT NULL,
    ALTER COLUMN linjaauto SET NOT NULL,
    ALTER COLUMN raitiovaunu SET NOT NULL;

-- These have been removed from the entity long ago.
ALTER TABLE tormaystarkastelutulos
    DROP COLUMN tila,
    DROP COLUMN tilachangedat;

-- Set a default for createdat, so it can used for maintenance.
-- Not mapped to the entity.
ALTER TABLE tormaystarkastelutulos
    ALTER COLUMN createdat SET DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN tormaystarkastelutulos.perus IS 'Nuisance index score for car traffic';
COMMENT ON COLUMN tormaystarkastelutulos.pyoraily IS 'Nuisance index score for car traffic'
