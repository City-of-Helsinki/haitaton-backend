--liquibase formatted sql
--changeset Topias Heinonen:022-add-trigger-for-removing-orphaned-geometriat
--comment: Add trigger for deleting orphaned geometriat after deleting a hankealue

CREATE FUNCTION clean_geometriat_after_delete()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
BEGIN
    IF OLD.geometriat IS NOT NULL THEN
        DELETE FROM geometriat WHERE id = OLD.geometriat;
    END IF;
    RETURN OLD;
END;
';

CREATE TRIGGER after_alue_delete
    AFTER DELETE
    ON hankealue
    FOR EACH ROW
EXECUTE FUNCTION clean_geometriat_after_delete();
