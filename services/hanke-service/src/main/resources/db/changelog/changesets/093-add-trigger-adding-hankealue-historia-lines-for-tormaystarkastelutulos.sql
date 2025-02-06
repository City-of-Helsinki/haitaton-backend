--liquibase formatted sql
--changeset Petri Timlin:093-add-trigger-adding-hankealue-historia-lines-for-tormaystarkastelutulos
--comment: Add trigger for checking changes of tormaystarkastelutulos and inserting rows to hankealue_historia

CREATE FUNCTION check_tormaystarkastelutulos_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    oldData hankealue_historia%rowtype;
  BEGIN
    IF (TG_OP = ''INSERT'') THEN
        -- Handle insert logic here
        select hh.* into oldData from hankealue_historia hh
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=NEW.hankealue_id);
        -- Check if hankegeometria is changed
        IF ((
            oldData.haittaindeksi_pyoraliikenne IS DISTINCT FROM NEW.pyoraliikenne OR
            oldData.haittaindeksi_autoliikenne IS DISTINCT FROM NEW.autoliikenne OR
            oldData.haittaindeksi_linja_autojen_paikallisliikenne IS DISTINCT FROM NEW.linjaautoliikenne OR
            oldData.haittaindeksi_raitioliikenne IS DISTINCT FROM NEW.raitioliikenne
            )
            and oldData.dml_id is not NULL) THEN
            oldData.haittaindeksi_pyoraliikenne := NEW.pyoraliikenne;
            oldData.haittaindeksi_autoliikenne := NEW.autoliikenne;
            oldData.haittaindeksi_linja_autojen_paikallisliikenne := NEW.linjaautoliikenne;
            oldData.haittaindeksi_raitioliikenne := NEW.raitioliikenne;
            oldData.dml_type := ''INSERT_TORMAYSTARKASTELUTULOS'';
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
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=NEW.hankealue_id);
        -- Check if hankegeometria is changed
        IF ((
            oldData.haittaindeksi_pyoraliikenne IS DISTINCT FROM NEW.pyoraliikenne OR
            oldData.haittaindeksi_autoliikenne IS DISTINCT FROM NEW.autoliikenne OR
            oldData.haittaindeksi_linja_autojen_paikallisliikenne IS DISTINCT FROM NEW.linjaautoliikenne OR
            oldData.haittaindeksi_raitioliikenne IS DISTINCT FROM NEW.raitioliikenne
            )
            and oldData.dml_id is not NULL) THEN
            oldData.haittaindeksi_pyoraliikenne := NEW.pyoraliikenne;
            oldData.haittaindeksi_autoliikenne := NEW.autoliikenne;
            oldData.haittaindeksi_linja_autojen_paikallisliikenne := NEW.linjaautoliikenne;
            oldData.haittaindeksi_raitioliikenne := NEW.raitioliikenne;
            oldData.dml_type := ''UPDATE_TORMAYSTARKASTELUTULOS'';
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
    ELSIF (TG_OP = ''DELETE_'') THEN
        -- Handle delete logic here
        select hh.* into oldData from hankealue_historia hh
        where hh.dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=OLD.hankealue_id);
        -- Check if hankegeometria is changed
        IF (oldData.dml_id is not NULL) THEN
            oldData.dml_type := ''DELETE_TORMAYSTARKASTELUTULOS'';
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

CREATE CONSTRAINT TRIGGER after_tormaystarkastelutulos_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON tormaystarkastelutulos
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_tormaystarkastelutulos_changes();