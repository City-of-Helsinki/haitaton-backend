--liquibase formatted sql
--changeset Topias Heinonen:075-change-tormaystarkastelutulos-to-refer-to-hankealue
--comment: Change Tormaystarkastelutulos to refer to hankealue instead of hanke. Change the individual results to numeric type instead of float.

DELETE
FROM tormaystarkastelutulos;

ALTER TABLE tormaystarkastelutulos
    DROP COLUMN hankeid,
    ADD COLUMN hankealue_id bigint NOT NULL,
    ADD CONSTRAINT fk_tormaystarkastelutulos_hankealue FOREIGN KEY (hankealue_id) REFERENCES hankealue (id);

ALTER TABLE tormaystarkastelutulos
    ALTER COLUMN autoliikenne TYPE numeric(2, 1),
    ALTER COLUMN pyoraliikenne TYPE numeric(2, 1),
    ALTER COLUMN linjaautoliikenne TYPE numeric(2, 1),
    ALTER COLUMN raitioliikenne TYPE numeric(2, 1);
