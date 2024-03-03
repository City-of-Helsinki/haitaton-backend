--liquibase formatted sql
--changeset Topias Heinonen:071-grant-delete-user-permission-to-kayttooikeustasot
--comment: Grant the MODIFY_USERS permission to all kayttooikeustasot except KATSELUOIKEUS

UPDATE kayttooikeustaso
SET permissioncode = permissioncode | 2048
WHERE kayttooikeustaso IN ('KAIKKI_OIKEUDET','KAIKKIEN_MUOKKAUS');
