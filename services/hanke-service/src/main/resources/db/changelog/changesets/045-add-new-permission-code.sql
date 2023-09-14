--liquibase formatted sql
--changeset Topias Heinonen:045-add-new-permission-code
--comment: Add RESEND_INVITATION permission code

UPDATE kayttooikeustaso
SET permissioncode = permissioncode | 256
WHERE kayttooikeustaso IN ('KAIKKI_OIKEUDET','KAIKKIEN_MUOKKAUS', 'HANKEMUOKKAUS', 'HAKEMUSASIOINTI');
