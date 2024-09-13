--liquibase formatted sql
--changeset Topias Heinonen:082-rename-ytunnus-to-registry-key-for-hakemusyhteystieto
--comment: Rename y_tunnus to registry_key for hakemusyhteystieto

ALTER TABLE hakemusyhteystieto RENAME y_tunnus TO registry_key;
