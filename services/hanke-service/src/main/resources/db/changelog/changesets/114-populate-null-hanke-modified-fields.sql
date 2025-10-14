--liquibase formatted sql
--changeset haitaton:114-populate-null-hanke-modified-fields
--comment: Set modifiedat and modifiedbyuserid to match createdat and createdbyuserid for hanke rows where they are currently null

UPDATE hanke
SET modifiedat = createdat,
    modifiedbyuserid = createdbyuserid
WHERE modifiedat IS NULL
   OR modifiedbyuserid IS NULL;
