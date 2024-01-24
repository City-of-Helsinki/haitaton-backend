--liquibase formatted sql
--changeset Topias Heinonen:068-grant-modify-user-permission-to-kayttooikeustasot
--comment: Grant the MODIFY_USERS permission to all kayttooikeustasot except KATSELUOIKEUS

UPDATE kayttooikeustaso
SET permissioncode = permissioncode | 1024
WHERE kayttooikeustaso IN ('KAIKKI_OIKEUDET','KAIKKIEN_MUOKKAUS', 'HANKEMUOKKAUS', 'HAKEMUSASIOINTI');
