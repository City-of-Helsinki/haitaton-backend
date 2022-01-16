--liquibase formatted sql
--changeset 003-update-permissions-to-creator comment:Add all current permissions to the user who created hanke
UPDATE permissions
SET permissioncode = 1 | 2 | 4 | 8
FROM hanke
WHERE permissions.userid = hanke.createdbyuserid
AND permissions.hankeid = hanke.id;