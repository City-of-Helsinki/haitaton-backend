--liquibase formatted sql
--changeset Petri Timlin:092-add-trigger-adding-hankealue-historia-lines-for-hankegeometria
--comment: Add trigger for checking changes of hankegeometria and inserting rows to hankealue_historia

CREATE FUNCTION check_hankegeometria_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    oldData hankealue_historia%rowtype;
  BEGIN
    IF (TG_OP = ''INSERT'') THEN
        -- Handle insert logic here
        select hh.* into oldData from hankealue_historia hh
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=(select ha.id from hankealue ha inner join hankegeometria h on (ha.geometriat=h.hankegeometriatid and h.id=NEW.id)));
        -- Check if hankegeometria is changed
        IF not(ST_Equals(NEW.geometria,oldData.hankealueen_geometria)) and oldData.dml_id is not NULL THEN
            oldData.hankealueen_geometria := NEW.geometria;
            --Haitaton creates always new hankegeometria line when geometria is changed in UI. That is why we set also insert as update.
            oldData.dml_type := ''UPDATE_HANKEGEOMETRIA'';
            oldData.dml_timestamp := CURRENT_TIMESTAMP;
            oldData.dml_created_by := CURRENT_USER;
            INSERT INTO hankealue_historia (
                hankealue_id, hanke_id,
                hanketunnus, alueen_nimi,
                haittojen_alkupaiva, haittojen_loppupaiva,
                meluhaitta, polyhaitta, tarinahaitta, autoliikenteen_kaistahaitta, kaistahaittojen_pituus,
                haittaindeksi_pyoraliikenne,
                haittaindeksi_autoliikenne,
                haittaindeksi_linja_autojen_paikallisliikenne,
                haittaindeksi_raitioliikenne,
                hankealueen_geometria,
                muutosaika,
                tallentaja_taho,
                dml_type, dml_timestamp, dml_created_by)
            VALUES (
                oldData.hankealue_id, oldData.hanke_id,
                oldData.hanketunnus, oldData.alueen_nimi,
                oldData.haittojen_alkupaiva, oldData.haittojen_loppupaiva,
                oldData.meluhaitta, oldData.polyhaitta, oldData.tarinahaitta, oldData.autoliikenteen_kaistahaitta, oldData.kaistahaittojen_pituus,
                oldData.haittaindeksi_pyoraliikenne,
                oldData.haittaindeksi_autoliikenne,
                oldData.haittaindeksi_linja_autojen_paikallisliikenne,
                oldData.haittaindeksi_raitioliikenne,
                oldData.hankealueen_geometria,
                oldData.muutosaika,
                oldData.tallentaja_taho,
                oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
        END IF;
    ELSIF (TG_OP = ''UPDATE'') THEN
        -- Handle update logic here
        select hh.* into oldData from hankealue_historia hh
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=(select ha.id from hankealue ha inner join hankegeometria h on (ha.geometriat=h.hankegeometriatid and h.id=NEW.id)));
        -- Check if hankegeometria is changed
        IF not(ST_Equals(NEW.geometria,oldData.hankealueen_geometria)) and oldData.dml_id is not NULL THEN
            oldData.hankealueen_geometria := NEW.geometria;
            oldData.dml_type := ''UPDATE_HANKEGEOMETRIA'';
            oldData.dml_timestamp := CURRENT_TIMESTAMP;
            oldData.dml_created_by := CURRENT_USER;
            INSERT INTO hankealue_historia (
                hankealue_id, hanke_id,
                hanketunnus, alueen_nimi,
                haittojen_alkupaiva, haittojen_loppupaiva,
                meluhaitta, polyhaitta, tarinahaitta, autoliikenteen_kaistahaitta, kaistahaittojen_pituus,
                haittaindeksi_pyoraliikenne,
                haittaindeksi_autoliikenne,
                haittaindeksi_linja_autojen_paikallisliikenne,
                haittaindeksi_raitioliikenne,
                hankealueen_geometria,
                muutosaika,
                tallentaja_taho,
                dml_type, dml_timestamp, dml_created_by)
            VALUES (
                oldData.hankealue_id, oldData.hanke_id,
                oldData.hanketunnus, oldData.alueen_nimi,
                oldData.haittojen_alkupaiva, oldData.haittojen_loppupaiva,
                oldData.meluhaitta, oldData.polyhaitta, oldData.tarinahaitta, oldData.autoliikenteen_kaistahaitta, oldData.kaistahaittojen_pituus,
                oldData.haittaindeksi_pyoraliikenne,
                oldData.haittaindeksi_autoliikenne,
                oldData.haittaindeksi_linja_autojen_paikallisliikenne,
                oldData.haittaindeksi_raitioliikenne,
                oldData.hankealueen_geometria,
                oldData.muutosaika,
                oldData.tallentaja_taho,
                oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
        END IF;
    ELSIF (TG_OP = ''DELETE'') THEN
        -- Handle delete logic here
        select hh.* into oldData from hankealue_historia hh
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=(select ha.id from hankealue ha inner join hankegeometria h on (ha.geometriat=h.hankegeometriatid and h.id=OLD.id)));
        IF oldData.dml_id is not NULL THEN
            oldData.dml_type := ''DELETE_HANKEGEOMETRIA'';
            oldData.dml_timestamp := CURRENT_TIMESTAMP;
            oldData.dml_created_by := CURRENT_USER;
            INSERT INTO hankealue_historia (
                hankealue_id, hanke_id,
                hanketunnus, alueen_nimi,
                haittojen_alkupaiva, haittojen_loppupaiva,
                meluhaitta, polyhaitta, tarinahaitta, autoliikenteen_kaistahaitta, kaistahaittojen_pituus,
                haittaindeksi_pyoraliikenne,
                haittaindeksi_autoliikenne,
                haittaindeksi_linja_autojen_paikallisliikenne,
                haittaindeksi_raitioliikenne,
                hankealueen_geometria,
                muutosaika,
                tallentaja_taho,
                dml_type, dml_timestamp, dml_created_by)
            VALUES (
                oldData.hankealue_id, oldData.hanke_id,
                oldData.hanketunnus, oldData.alueen_nimi,
                oldData.haittojen_alkupaiva, oldData.haittojen_loppupaiva,
                oldData.meluhaitta, oldData.polyhaitta, oldData.tarinahaitta, oldData.autoliikenteen_kaistahaitta, oldData.kaistahaittojen_pituus,
                oldData.haittaindeksi_pyoraliikenne,
                oldData.haittaindeksi_autoliikenne,
                oldData.haittaindeksi_linja_autojen_paikallisliikenne,
                oldData.haittaindeksi_raitioliikenne,
                oldData.hankealueen_geometria,
                oldData.muutosaika,
                oldData.tallentaja_taho,
                oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
        END IF;
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_hankegeometria_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON hankegeometria
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_hankegeometria_changes();