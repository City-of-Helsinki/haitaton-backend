--liquibase formatted sql
--changeset Petri Timlin:103-replace-function-check-kaivuilmoitus-changes-to-fix-toiminnallinen-kunto-and-tyo-valmis-dates
--comment: Fixing function check_kaivuilmoitus_changes to handle dates correctly

CREATE OR REPLACE FUNCTION check_kaivuilmoitus_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    tyostaVastaavaNew text;
    tyostaVastaavaHist text;
    tyonSuorittajaNew text;
    tyonSuorittajaHist text;
    rakennuttajaNew text;
    rakennuttajaHist text;
    asianhoitajaNew text;
    asianhoitajaHist text;
    laskutettavaNew text;
    laskutettavaHist text;
    tallentajaNew text[];
    tallentajaHist text[];
    toiminnallisen_kunnon_ilmoitettu_paivaNew timestamp;
    toiminnallisen_kunnon_ilmoitettu_paivaHist timestamp;
    tyo_valmis_ilmoitettu_paivaNew timestamp;
    tyo_valmis_ilmoitettu_paivaHist timestamp;
    oldData kaivuilmoitus_historia%rowtype;
  BEGIN
    IF (TG_OP = ''INSERT'' AND NEW.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle insert logic here
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
            (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
            NEW.id, NEW.applicationidentifier, NEW.allustatus,
            NEW.applicationdata ->> ''name'',
            NEW.applicationdata ->> ''workDescription'',
            NEW.applicationdata ->> ''constructionWork'',
            NEW.applicationdata ->> ''maintenanceWork'',
            NEW.applicationdata ->> ''emergencyWork'',
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''cableReports'')),'',''),
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''placementContracts'')),'',''),
            to_timestamp(cast(NEW.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(NEW.applicationdata ->> ''endTime'' as decimal)),
            (select date_reported from valmistumisilmoitus where application_id=NEW.id and type=''TOIMINNALLINEN_KUNTO'' order by created_at desc limit 1),
            (select date_reported from valmistumisilmoitus where application_id=NEW.id and type=''TYO_VALMIS'' order by created_at desc limit 1),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA''),
            (select
                case
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''PERSON'' then ''YKSITYISHENKILO''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''ASSOCIATION'' then ''YHDISTYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''COMPANY'' then ''YRITYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''OTHER'' then ''MUU''
                    else null
                end
            ),
            CURRENT_TIMESTAMP,
            (select array_agg(h4.rooli) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
            ''INSERT'', CURRENT_TIMESTAMP, CURRENT_USER);
    ELSIF (TG_OP = ''UPDATE'' AND NEW.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle update logic here

        select coalesce(array_agg(h4.tyyppi),array[]::text[]) into tallentajaNew from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id;
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into tyostaVastaavaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into tyonSuorittajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into rakennuttajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into asianhoitajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA'';

        select date_reported into toiminnallisen_kunnon_ilmoitettu_paivaNew from valmistumisilmoitus where application_id=NEW.id and type=''TOIMINNALLINEN_KUNTO'' order by created_at desc limit 1;
        select date_reported into tyo_valmis_ilmoitettu_paivaNew from valmistumisilmoitus where application_id=NEW.id and type=''TYO_VALMIS'' order by created_at desc limit 1;

        select coalesce(tallentaja_taho,array[]::text[]), tyosta_vastaava, tyon_suorittaja, rakennuttaja, asianhoitaja, toiminnallisen_kunnon_ilmoitettu_paiva, tyo_valmis_ilmoitettu_paiva, laskutettava
            into tallentajaHist, tyostaVastaavaHist, tyonSuorittajaHist, rakennuttajaHist, asianhoitajaHist, toiminnallisen_kunnon_ilmoitettu_paivaHist, tyo_valmis_ilmoitettu_paivaHist, laskutettavaHist
        from kaivuilmoitus_historia where hakemuksen_id=NEW.id and dml_id=(select max(dml_id) from kaivuilmoitus_historia where hakemuksen_id=NEW.id);
        -- Check if any of the fields have changed
        IF (
            OLD.applicationidentifier IS DISTINCT FROM NEW.applicationidentifier OR
            OLD.allustatus IS DISTINCT FROM NEW.allustatus OR
            OLD.applicationdata ->> ''name'' IS DISTINCT FROM NEW.applicationdata ->> ''name'' OR
            OLD.applicationdata ->> ''workDescription'' IS DISTINCT FROM NEW.applicationdata ->> ''workDescription'' OR
            OLD.applicationdata ->> ''constructionWork'' IS DISTINCT FROM NEW.applicationdata ->> ''constructionWork'' OR
            OLD.applicationdata ->> ''maintenanceWork'' IS DISTINCT FROM NEW.applicationdata ->> ''maintenanceWork'' OR
            OLD.applicationdata ->> ''emergencyWork'' IS DISTINCT FROM NEW.applicationdata ->> ''emergencyWork'' OR
            OLD.applicationdata -> ''startTime'' IS DISTINCT FROM NEW.applicationdata -> ''startTime'' OR
            OLD.applicationdata -> ''endTime'' IS DISTINCT FROM NEW.applicationdata -> ''endTime'' OR
            toiminnallisen_kunnon_ilmoitettu_paivaHist IS DISTINCT FROM toiminnallisen_kunnon_ilmoitettu_paivaNew OR
            tyo_valmis_ilmoitettu_paivaHist IS DISTINCT FROM tyo_valmis_ilmoitettu_paivaNew OR
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(OLD.applicationdata)->''cableReports'')),'','') IS DISTINCT FROM array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''cableReports'')),'','') OR
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(OLD.applicationdata)->''placementContracts'')),'','') IS DISTINCT FROM array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''placementContracts'')),'','') OR
            tyostaVastaavaNew IS DISTINCT FROM tyostaVastaavaHist OR
            tyonSuorittajaNew IS DISTINCT FROM tyonSuorittajaHist OR
            rakennuttajaNew IS DISTINCT FROM rakennuttajaHist OR
            asianhoitajaNew IS DISTINCT FROM asianhoitajaHist OR
            laskutettavaHist IS DISTINCT FROM (select
                case
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''PERSON'' then ''YKSITYISHENKILO''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''ASSOCIATION'' then ''YHDISTYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''COMPANY'' then ''YRITYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''OTHER'' then ''MUU''
                    else null
                end
            ) OR
            NOT(tallentajaNew @> tallentajaHist and tallentajaNew <@ tallentajaHist)
            ) THEN
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
            (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
            NEW.id, NEW.applicationidentifier, NEW.allustatus,
            NEW.applicationdata ->> ''name'',
            NEW.applicationdata ->> ''workDescription'',
            NEW.applicationdata ->> ''constructionWork'',
            NEW.applicationdata ->> ''maintenanceWork'',
            NEW.applicationdata ->> ''emergencyWork'',
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''cableReports'')),'',''),
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(NEW.applicationdata)->''placementContracts'')),'',''),
            to_timestamp(cast(NEW.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(NEW.applicationdata ->> ''endTime'' as decimal)),
            (select date_reported from valmistumisilmoitus where application_id=NEW.id and type=''TOIMINNALLINEN_KUNTO'' order by created_at desc limit 1),
            (select date_reported from valmistumisilmoitus where application_id=NEW.id and type=''TYO_VALMIS'' order by created_at desc limit 1),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA''),
            (select
                case
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''PERSON'' then ''YKSITYISHENKILO''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''ASSOCIATION'' then ''YHDISTYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''COMPANY'' then ''YRITYS''
                    when NEW.applicationdata -> ''invoicingCustomer''->>''type''=''OTHER'' then ''MUU''
                    else null
                end
            ),
            CURRENT_TIMESTAMP,
            (select array_agg(h4.rooli) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
            ''UPDATE'', CURRENT_TIMESTAMP, CURRENT_USER);
        END IF;
    ELSIF (TG_OP = ''DELETE'' AND OLD.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle delete logic here
        select hh.* into oldData from kaivuilmoitus_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitus_historia where hakemuksen_id=OLD.id);
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
            OLD.id, OLD.applicationidentifier, OLD.allustatus,
            OLD.applicationdata ->> ''name'',
            OLD.applicationdata ->> ''workDescription'',
            OLD.applicationdata ->> ''constructionWork'',
            OLD.applicationdata ->> ''maintenanceWork'',
            OLD.applicationdata ->> ''emergencyWork'',
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(OLD.applicationdata)->''cableReports'')),'',''),
            array_to_string(array(select jsonb_array_elements_text(jsonb_strip_nulls(OLD.applicationdata)->''placementContracts'')),'',''),
            to_timestamp(cast(OLD.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(OLD.applicationdata ->> ''endTime'' as decimal)),
            oldData.toiminnallisen_kunnon_ilmoitettu_paiva,
            oldData.tyo_valmis_ilmoitettu_paiva,
            oldData.tyosta_vastaava,
            oldData.tyon_suorittaja,
            oldData.rakennuttaja,
            oldData.asianhoitaja,
            oldData.laskutettava,
            CURRENT_TIMESTAMP,
            oldData.tallentaja_taho,
            ''DELETE'', CURRENT_TIMESTAMP, CURRENT_USER);
    END IF;
    RETURN NEW;
  END;
';