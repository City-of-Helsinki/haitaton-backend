--liquibase formatted sql
--changeset Topias Heinonen:071-grant-delete-user-permission-to-kayttooikeustasot
--comment: Grant the DELETE_USER permission to the two highest kayttooikeustaso.

UPDATE kayttooikeustaso
SET permissioncode = permissioncode | 2048
WHERE kayttooikeustaso IN ('KAIKKI_OIKEUDET','KAIKKIEN_MUOKKAUS');
