--liquibase formatted sql
--changeset Topias Heinonen:084-remove-datalock-from-hankeyhteystieto
--comment: Remove the columns related to locking yhteystieto data

ALTER TABLE hankeyhteystieto
    DROP COLUMN datalocked,
    DROP COLUMN datalockinfo;
