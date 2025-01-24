--liquibase formatted sql
--changeset Petri Timlin:096-add-trigger-adding-johtoselvitys-historia-lines-for-hakemusyhteystieto
--comment: Add trigger for checking changes of hakemusyhteystieto and inserting rows to johtoselvitys_historia

CREATE FUNCTION check_hakemusyhteystieto_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    oldData johtoselvitys_historia%rowtype;
    applicationTypeCheck varchar(40);
    tallentajaNew text[];
    tallentajaHist text[];
  BEGIN
    applicationTypeCheck:=NULL;
    IF (TG_OP = ''INSERT'') THEN
        -- Handle insert logic here
        select applicationtype into applicationTypeCheck from applications where id=NEW.application_id;
        IF (applicationTypeCheck=''CABLE_REPORT'') THEN
            select hh.* into oldData from johtoselvitys_historia hh where hh.dml_id=(select max(dml_id) from johtoselvitys_historia where hakemuksen_id=NEW.application_id);
            select coalesce(array_agg(h4.tyyppi),array[]::text[]) into tallentajaNew from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.application_id;
            IF NEW.tyyppi=''PERSON'' THEN
                NEW.nimi:=''YKSITYISHENKILO'';
            END IF;
            -- Check if hakemusyhteystieto is changed
            IF (
                (oldData.tyosta_vastaava IS DISTINCT FROM NEW.nimi AND NEW.rooli=''HAKIJA'') OR
                (oldData.tyon_suorittaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''TYON_SUORITTAJA'') OR
                (oldData.rakennuttaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''RAKENNUTTAJA'') OR
                (oldData.asianhoitaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''ASIANHOITAJA'') OR
                NOT(tallentajaNew @> oldData.tallentaja_taho and tallentajaNew <@ oldData.tallentaja_taho)
                ) THEN
                    IF (NEW.rooli=''HAKIJA'') THEN
                        oldData.tyosta_vastaava := NEW.nimi;
                    ELSIF (NEW.rooli=''TYON_SUORITTAJA'') THEN
                        oldData.tyon_suorittaja := NEW.nimi;
                    ELSIF (NEW.rooli=''RAKENNUTTAJA'') THEN
                        oldData.rakennuttaja := NEW.nimi;
                    ELSIF (NEW.rooli=''ASIANHOITAJA'') THEN
                        oldData.asianhoitaja := NEW.nimi;
                    END IF;
                    oldData.tallentaja_taho := tallentajaNew;
                    oldData.dml_type := ''INSERT_HAKEMUSYHTEYSTIETO'';
                    oldData.dml_timestamp := CURRENT_TIMESTAMP;
                    oldData.dml_created_by := CURRENT_USER;
                    INSERT INTO johtoselvitys_historia (
                        hanketunnus,
                        hakemuksen_id, hakemuksen_tunnus, hakemuksen_tila,
                        tyon_nimi,
                        katuosoite,
                        uuden_rakenteen_tai_johdon_rakentamisesta,
                        olemassaolevan_rakenteen_kunnossapitotyosta,
                        kiinteistoliittymien_rakentamisesta,
                        kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        louhitaanko,
                        tyon_kuvaus,
                        arvioitu_alkupaiva,
                        arvioitu_loppupaiva,
                        tyosta_vastaava,
                        tyon_suorittaja,
                        rakennuttaja,
                        asianhoitaja,
                        muutosaika,
                        tallentaja_taho,
                        dml_type, dml_timestamp, dml_created_by)
                    VALUES (
                        oldData.hanketunnus,
                        oldData.hakemuksen_id, oldData.hakemuksen_tunnus, oldData.hakemuksen_tila,
                        oldData.tyon_nimi,
                        oldData.katuosoite,
                        oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                        oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                        oldData.kiinteistoliittymien_rakentamisesta,
                        oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        oldData.louhitaanko,
                        oldData.tyon_kuvaus,
                        oldData.arvioitu_alkupaiva,
                        oldData.arvioitu_loppupaiva,
                        oldData.tyosta_vastaava,
                        oldData.tyon_suorittaja,
                        oldData.rakennuttaja,
                        oldData.asianhoitaja,
                        oldData.muutosaika,
                        oldData.tallentaja_taho,
                        oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
            END IF;
        END IF;
    ELSIF (TG_OP = ''UPDATE'') THEN
        -- Handle update logic here
        select applicationtype into applicationTypeCheck from applications where id=NEW.application_id;
        IF (applicationTypeCheck=''CABLE_REPORT'') THEN
            select hh.* into oldData from johtoselvitys_historia hh where hh.dml_id=(select max(dml_id) from johtoselvitys_historia where hakemuksen_id=NEW.application_id);
            select coalesce(array_agg(h4.tyyppi),array[]::text[]) into tallentajaNew from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.application_id;
            IF NEW.tyyppi=''PERSON'' THEN
                NEW.nimi:=''YKSITYISHENKILO'';
            END IF;
            -- Check if hakemusyhteystieto is changed
            IF (
                (oldData.tyosta_vastaava IS DISTINCT FROM NEW.nimi AND NEW.rooli=''HAKIJA'') OR
                (oldData.tyon_suorittaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''TYON_SUORITTAJA'') OR
                (oldData.rakennuttaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''RAKENNUTTAJA'') OR
                (oldData.asianhoitaja IS DISTINCT FROM NEW.nimi AND NEW.rooli=''ASIANHOITAJA'') OR
                NOT(tallentajaNew @> oldData.tallentaja_taho and tallentajaNew <@ oldData.tallentaja_taho)
                ) THEN
                    IF (NEW.rooli=''HAKIJA'') THEN
                        oldData.tyosta_vastaava := NEW.nimi;
                    ELSIF (NEW.rooli=''TYON_SUORITTAJA'') THEN
                        oldData.tyon_suorittaja := NEW.nimi;
                    ELSIF (NEW.rooli=''RAKENNUTTAJA'') THEN
                        oldData.rakennuttaja := NEW.nimi;
                    ELSIF (NEW.rooli=''ASIANHOITAJA'') THEN
                        oldData.asianhoitaja := NEW.nimi;
                    END IF;
                    oldData.tallentaja_taho := tallentajaNew;
                    oldData.dml_type := ''UPDATE_HAKEMUSYHTEYSTIETO'';
                    oldData.dml_timestamp := CURRENT_TIMESTAMP;
                    oldData.dml_created_by := CURRENT_USER;
                    INSERT INTO johtoselvitys_historia (
                        hanketunnus,
                        hakemuksen_id, hakemuksen_tunnus, hakemuksen_tila,
                        tyon_nimi,
                        katuosoite,
                        uuden_rakenteen_tai_johdon_rakentamisesta,
                        olemassaolevan_rakenteen_kunnossapitotyosta,
                        kiinteistoliittymien_rakentamisesta,
                        kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        louhitaanko,
                        tyon_kuvaus,
                        arvioitu_alkupaiva,
                        arvioitu_loppupaiva,
                        tyosta_vastaava,
                        tyon_suorittaja,
                        rakennuttaja,
                        asianhoitaja,
                        muutosaika,
                        tallentaja_taho,
                        dml_type, dml_timestamp, dml_created_by)
                    VALUES (
                        oldData.hanketunnus,
                        oldData.hakemuksen_id, oldData.hakemuksen_tunnus, oldData.hakemuksen_tila,
                        oldData.tyon_nimi,
                        oldData.katuosoite,
                        oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                        oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                        oldData.kiinteistoliittymien_rakentamisesta,
                        oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                        oldData.louhitaanko,
                        oldData.tyon_kuvaus,
                        oldData.arvioitu_alkupaiva,
                        oldData.arvioitu_loppupaiva,
                        oldData.tyosta_vastaava,
                        oldData.tyon_suorittaja,
                        oldData.rakennuttaja,
                        oldData.asianhoitaja,
                        oldData.muutosaika,
                        oldData.tallentaja_taho,
                        oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
            END IF;
        END IF;
    ELSIF (TG_OP = ''DELETE'') THEN
        -- Handle delete logic here
        select applicationtype into applicationTypeCheck from applications where id=OLD.application_id;
        IF (applicationTypeCheck=''CABLE_REPORT'') THEN
            select hh.* into oldData from johtoselvitys_historia hh where hh.dml_id=(select max(dml_id) from johtoselvitys_historia where hakemuksen_id=OLD.application_id);
            select coalesce(array_agg(h4.tyyppi),array[]::text[]) into tallentajaNew from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=OLD.application_id;
            IF OLD.tyyppi=''PERSON'' THEN
                OLD.nimi:=''YKSITYISHENKILO'';
            END IF;
            IF (OLD.rooli=''HAKIJA'') THEN
                oldData.tyosta_vastaava := null;
            ELSIF (OLD.rooli=''TYON_SUORITTAJA'') THEN
                oldData.tyon_suorittaja := null;
            ELSIF (OLD.rooli=''RAKENNUTTAJA'') THEN
                oldData.rakennuttaja := null;
            ELSIF (OLD.rooli=''ASIANHOITAJA'') THEN
                oldData.asianhoitaja := null;
            END IF;
            oldData.tallentaja_taho := tallentajaNew;
            oldData.dml_type := ''DELETE_HAKEMUSYHTEYSTIETO'';
            oldData.dml_timestamp := CURRENT_TIMESTAMP;
            oldData.dml_created_by := CURRENT_USER;
            INSERT INTO johtoselvitys_historia (
                hanketunnus,
                hakemuksen_id, hakemuksen_tunnus, hakemuksen_tila,
                tyon_nimi,
                katuosoite,
                uuden_rakenteen_tai_johdon_rakentamisesta,
                olemassaolevan_rakenteen_kunnossapitotyosta,
                kiinteistoliittymien_rakentamisesta,
                kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                louhitaanko,
                tyon_kuvaus,
                arvioitu_alkupaiva,
                arvioitu_loppupaiva,
                tyosta_vastaava,
                tyon_suorittaja,
                rakennuttaja,
                asianhoitaja,
                muutosaika,
                tallentaja_taho,
                dml_type, dml_timestamp, dml_created_by)
            VALUES (
                oldData.hanketunnus,
                oldData.hakemuksen_id, oldData.hakemuksen_tunnus, oldData.hakemuksen_tila,
                oldData.tyon_nimi,
                oldData.katuosoite,
                oldData.uuden_rakenteen_tai_johdon_rakentamisesta,
                oldData.olemassaolevan_rakenteen_kunnossapitotyosta,
                oldData.kiinteistoliittymien_rakentamisesta,
                oldData.kaivutyo_on_aloitettu_ennen_johtoselvityksen_tilaamista,
                oldData.louhitaanko,
                oldData.tyon_kuvaus,
                oldData.arvioitu_alkupaiva,
                oldData.arvioitu_loppupaiva,
                oldData.tyosta_vastaava,
                oldData.tyon_suorittaja,
                oldData.rakennuttaja,
                oldData.asianhoitaja,
                oldData.muutosaika,
                oldData.tallentaja_taho,
                oldData.dml_type, oldData.dml_timestamp, oldData.dml_created_by);
        END IF;
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_hakemusyhteystieto_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON hakemusyhteystieto
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_hakemusyhteystieto_changes();