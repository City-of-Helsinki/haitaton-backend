--liquibase formatted sql
--changeset Topias Heinonen:075-remove-old-cycle-tormays-tables
--comment: Remove the old cycle tormays tables.

DELETE
FROM tormaystarkastelutulos;

ALTER TABLE tormaystarkastelutulos
    DROP COLUMN hankeid,
    ADD COLUMN hankealue_id bigint NOT NULL,
    ADD CONSTRAINT fk_tormaystarkastelutulos_hankealue FOREIGN KEY (hankealue_id) REFERENCES hankealue (id);
