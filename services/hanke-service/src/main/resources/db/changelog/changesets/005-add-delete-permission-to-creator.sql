--liquibase formatted sql
--changeset 005-add-delete-permission-to-creator comment:Add delete permission to the user who created that hanke
UPDATE permissions
SET permissioncode = 16
FROM hanke
WHERE permissions.userid = hanke.createdbyuserid
AND permissions.hankeid = hanke.id;