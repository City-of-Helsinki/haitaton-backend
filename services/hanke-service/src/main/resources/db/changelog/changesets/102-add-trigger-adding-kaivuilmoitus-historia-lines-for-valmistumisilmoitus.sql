--liquibase formatted sql
--changeset Petri Timlin:102-add-trigger-adding-kaivuilmoitus-historia-lines-for-valmistumisilmoitus
--comment: Add trigger for checking changes of valmistumisilmoitus and inserting rows to kaivuilmoitus_historia

CREATE FUNCTION check_kaivuilmoitus_valmistumisilmoitus_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    oldData kaivuilmoitus_historia%rowtype;
    applicationTypeCheck varchar(40);
  BEGIN
    applicationTypeCheck:=NULL;
    IF (TG_OP = ''INSERT'') THEN
        -- Handle insert logic here
        select applicationtype into applicationTypeCheck from applications where id=NEW.application_id;
        IF (applicationTypeCheck=''EXCAVATION_NOTIFICATION'') THEN

            select hh.* into oldData from kaivuilmoitus_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitus_historia where hakemuksen_id=NEW.application_id);
            -- Check if valmistumisilmoitus is changed
            IF (
                (oldData.toiminnallisen_kunnon_ilmoitettu_paiva IS DISTINCT FROM NEW.date_reported AND NEW.type=''TOIMINNALLINEN_KUNTO'') OR
                (oldData.tyo_valmis_ilmoitettu_paiva IS DISTINCT FROM NEW.date_reported AND NEW.type=''TYO_VALMIS'')
                ) THEN
                    IF NEW.type=''TOIMINNALLINEN_KUNTO'' THEN
                        oldData.toiminnallisen_kunnon_ilmoitettu_paiva := NEW.date_reported;
                    ELSIF NEW.type=''TYO_VALMIS'' THEN
                        oldData.tyo_valmis_ilmoitettu_paiva := NEW.date_reported;
                    END IF;
                    oldData.dml_type := ''INSERT_KAIVUILMOITUS_VALMISTUMISILMOITUS'';
                    oldData.dml_timestamp := CURRENT_TIMESTAMP;
                    oldData.dml_created_by := CURRENT_USER;
                    INSERT INTO kaivuilmoitus_historia (
                        hanketunnus,
                        hakemuksen_id,
                        hakemuksen_tunnus,
                        hakemuksen_tila,
                        tyon_nimi,
                        tyon_kuvaus,
                        uuden_rakenteen_tai_johdon_rakentamisesta,
                        olemassaolevan_rakenteen_kunnossapitotyosta,
                        kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        liittyvien_johtoselvitysten_tunnukset,
                        liittyvien_sijoitussopimusten_tunnukset,
                        tyon_alkupaiva,
                        tyon_loppupaiva,
                        toiminnallisen_kunnon_ilmoitettu_paiva,
                        tyo_valmis_ilmoitettu_paiva,
                        tyosta_vastaava,
                        tyon_suorittaja,
                        rakennuttaja,
                        asianhoitaja,
                        laskutettava,
                        muutosaika,
                        tallentaja_taho,
                        dml_type,
                        dml_timestamp,
                        dml_created_by)
                     VALUES (
                        oldData.hanketunnus,
                        oldData.hakemuksen_id,
                        oldData.hakemuksen_tunnus,
                        oldData.hakemuksen_tila,
                        oldData.tyon_nimi,
                        oldData.tyon_kuvaus,
                        oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                        oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                        oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        oldData.liittyvien_johtoselvitysten_tunnukset,
                        oldData.liittyvien_sijoitussopimusten_tunnukset,
                        oldData.tyon_alkupaiva,
                        oldData.tyon_loppupaiva,
                        oldData.toiminnallisen_kunnon_ilmoitettu_paiva,
                        oldData.tyo_valmis_ilmoitettu_paiva,
                        oldData.tyosta_vastaava,
                        oldData.tyon_suorittaja,
                        oldData.rakennuttaja,
                        oldData.asianhoitaja,
                        oldData.laskutettava,
                        oldData.muutosaika,
                        oldData.tallentaja_taho,
                        oldData.dml_type,
                        oldData.dml_timestamp,
                        oldData.dml_created_by);
            END IF;
        END IF;
    ELSIF (TG_OP = ''UPDATE'') THEN
        -- Handle update logic here
        select applicationtype into applicationTypeCheck from applications where id=NEW.application_id;
        IF (applicationTypeCheck=''EXCAVATION_NOTIFICATION'') THEN
            select hh.* into oldData from kaivuilmoitus_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitus_historia where hakemuksen_id=NEW.application_id);
            -- Check if valmistumisilmoitus is changed
            IF (
                (oldData.toiminnallisen_kunnon_ilmoitettu_paiva IS DISTINCT FROM NEW.date_reported AND NEW.type=''TOIMINNALLINEN_KUNTO'') OR
                (oldData.tyo_valmis_ilmoitettu_paiva IS DISTINCT FROM NEW.date_reported AND NEW.type=''TYO_VALMIS'')
                ) THEN
                    IF NEW.type=''TOIMINNALLINEN_KUNTO'' THEN
                        oldData.toiminnallisen_kunnon_ilmoitettu_paiva := NEW.date_reported;
                    ELSIF NEW.type=''TYO_VALMIS'' THEN
                        oldData.tyo_valmis_ilmoitettu_paiva := NEW.date_reported;
                    END IF;
                    oldData.dml_type := ''UPDATE_KAIVUILMOITUS_VALMISTUMISILMOITUS'';
                    oldData.dml_timestamp := CURRENT_TIMESTAMP;
                    oldData.dml_created_by := CURRENT_USER;
                    INSERT INTO kaivuilmoitus_historia (
                        hanketunnus,
                        hakemuksen_id,
                        hakemuksen_tunnus,
                        hakemuksen_tila,
                        tyon_nimi,
                        tyon_kuvaus,
                        uuden_rakenteen_tai_johdon_rakentamisesta,
                        olemassaolevan_rakenteen_kunnossapitotyosta,
                        kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        liittyvien_johtoselvitysten_tunnukset,
                        liittyvien_sijoitussopimusten_tunnukset,
                        tyon_alkupaiva,
                        tyon_loppupaiva,
                        toiminnallisen_kunnon_ilmoitettu_paiva,
                        tyo_valmis_ilmoitettu_paiva,
                        tyosta_vastaava,
                        tyon_suorittaja,
                        rakennuttaja,
                        asianhoitaja,
                        laskutettava,
                        muutosaika,
                        tallentaja_taho,
                        dml_type,
                        dml_timestamp,
                        dml_created_by)
                     VALUES (
                        oldData.hanketunnus,
                        oldData.hakemuksen_id,
                        oldData.hakemuksen_tunnus,
                        oldData.hakemuksen_tila,
                        oldData.tyon_nimi,
                        oldData.tyon_kuvaus,
                        oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                        oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                        oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        oldData.liittyvien_johtoselvitysten_tunnukset,
                        oldData.liittyvien_sijoitussopimusten_tunnukset,
                        oldData.tyon_alkupaiva,
                        oldData.tyon_loppupaiva,
                        oldData.toiminnallisen_kunnon_ilmoitettu_paiva,
                        oldData.tyo_valmis_ilmoitettu_paiva,
                        oldData.tyosta_vastaava,
                        oldData.tyon_suorittaja,
                        oldData.rakennuttaja,
                        oldData.asianhoitaja,
                        oldData.laskutettava,
                        oldData.muutosaika,
                        oldData.tallentaja_taho,
                        oldData.dml_type,
                        oldData.dml_timestamp,
                        oldData.dml_created_by);
            END IF;
        END IF;
    ELSIF (TG_OP = ''DELETE'') THEN
        -- Handle delete logic here
        select applicationtype into applicationTypeCheck from applications where id=OLD.application_id;
        IF (applicationTypeCheck=''EXCAVATION_NOTIFICATION'') THEN
            select hh.* into oldData from kaivuilmoitus_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitus_historia where hakemuksen_id=OLD.application_id);
            IF OLD.type=''TOIMINNALLINEN_KUNTO'' THEN
                oldData.toiminnallisen_kunnon_ilmoitettu_paiva := NULL;
            ELSIF OLD.type=''TYO_VALMIS'' THEN
                oldData.tyo_valmis_ilmoitettu_paiva := NULL;
            END IF;
            oldData.dml_type := ''DELETE_KAIVUILMOITUS_VALMISTUMISILMOITUS'';
            oldData.dml_timestamp := CURRENT_TIMESTAMP;
            oldData.dml_created_by := CURRENT_USER;
            INSERT INTO kaivuilmoitus_historia (
                hanketunnus,
                hakemuksen_id,
                hakemuksen_tunnus,
                hakemuksen_tila,
                tyon_nimi,
                tyon_kuvaus,
                uuden_rakenteen_tai_johdon_rakentamisesta,
                olemassaolevan_rakenteen_kunnossapitotyosta,
                kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                liittyvien_johtoselvitysten_tunnukset,
                liittyvien_sijoitussopimusten_tunnukset,
                tyon_alkupaiva,
                tyon_loppupaiva,
                toiminnallisen_kunnon_ilmoitettu_paiva,
                tyo_valmis_ilmoitettu_paiva,
                tyosta_vastaava,
                tyon_suorittaja,
                rakennuttaja,
                asianhoitaja,
                laskutettava,
                muutosaika,
                tallentaja_taho,
                dml_type,
                dml_timestamp,
                dml_created_by)
             VALUES (
                oldData.hanketunnus,
                oldData.hakemuksen_id,
                oldData.hakemuksen_tunnus,
                oldData.hakemuksen_tila,
                oldData.tyon_nimi,
                oldData.tyon_kuvaus,
                oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                oldData.liittyvien_johtoselvitysten_tunnukset,
                oldData.liittyvien_sijoitussopimusten_tunnukset,
                oldData.tyon_alkupaiva,
                oldData.tyon_loppupaiva,
                oldData.toiminnallisen_kunnon_ilmoitettu_paiva,
                oldData.tyo_valmis_ilmoitettu_paiva,
                oldData.tyosta_vastaava,
                oldData.tyon_suorittaja,
                oldData.rakennuttaja,
                oldData.asianhoitaja,
                oldData.laskutettava,
                oldData.muutosaika,
                oldData.tallentaja_taho,
                oldData.dml_type,
                oldData.dml_timestamp,
                oldData.dml_created_by);
        END IF;
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_kaivuilmoitus_valmistumisilmoitus_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON valmistumisilmoitus
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_kaivuilmoitus_valmistumisilmoitus_changes();