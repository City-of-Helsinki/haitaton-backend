--liquibase formatted sql
--changeset Topias Heinonen:064-add-create-user-permission-code
--comment: Add CREATE_USER permission code

UPDATE kayttooikeustaso
SET permissioncode = permissioncode | 512
WHERE kayttooikeustaso IN ('KAIKKI_OIKEUDET','KAIKKIEN_MUOKKAUS', 'HANKEMUOKKAUS', 'HAKEMUSASIOINTI');
