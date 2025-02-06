--liquibase formatted sql
--changeset Petri Timlin:095-add-trigger-adding-johtoselvitys-historia-lines-for-applications
--comment: Add trigger for checking changes of johtoselvitys and inserting rows to johtoselvitys_historia

CREATE FUNCTION check_johtoselvitys_changes()
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
    tallentajaNew text[];
    tallentajaHist text[];
    geomNew geometry;
    geomHist geometry;
    pintaAlaNew numeric;
    pintaAlaHist numeric;
    myId integer;
    oldData johtoselvitys_historia%rowtype;
  BEGIN
    IF (TG_OP = ''INSERT'' AND NEW.applicationtype=''CABLE_REPORT'') THEN
        -- Handle insert logic here
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
            pinta_ala,
            geometria,
            muutosaika,
            tallentaja_taho,
            dml_type, dml_timestamp, dml_created_by)
        VALUES (
            (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
            NEW.id, NEW.applicationidentifier, NEW.allustatus,
            NEW.applicationdata ->> ''name'',
            trim(NEW.applicationdata -> ''postalAddress'' -> ''streetAddress'' ->> ''streetName''),
            NEW.applicationdata ->> ''constructionWork'',
            NEW.applicationdata ->> ''maintenanceWork'',
            NEW.applicationdata ->> ''propertyConnectivity'',
            NEW.applicationdata ->> ''emergencyWork'',
            NEW.applicationdata ->> ''rockExcavation'',
            NEW.applicationdata ->> ''workDescription'',
            to_timestamp(cast(NEW.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(NEW.applicationdata ->> ''endTime'' as decimal)),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA''),
            null,
            null,
            CURRENT_TIMESTAMP,
            (select array_agg(h4.tyyppi) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
            ''INSERT'', CURRENT_TIMESTAMP, CURRENT_USER);
    ELSIF (TG_OP = ''UPDATE'' AND NEW.applicationtype=''CABLE_REPORT'') THEN
        -- Handle update logic here
        begin
            select a.id, st_multi(st_union(ST_GeomFromGeoJSON((geom_json->''geometry'')))), round(st_area(st_multi(st_union(ST_GeomFromGeoJSON((geom_json->''geometry'')))))) into myId, geomNew, pintaAlaNew
                from applications a left join lateral jsonb_array_elements(jsonb_strip_nulls(a.applicationdata)->''areas'') as geom_json on true where a.applicationtype=''CABLE_REPORT'' and a.id=NEW.id group by 1 order by id;
            exception
                when others then
                    pintaAlaNew:=null;
                    geomNew:=null;
        end;

        select coalesce(array_agg(h4.tyyppi),array[]::text[]) into tallentajaNew from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id;
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into tyostaVastaavaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into tyonSuorittajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into rakennuttajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA'';
        select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end into asianhoitajaNew from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA'';

        select coalesce(tallentaja_taho,array[]::text[]), tyosta_vastaava, tyon_suorittaja, rakennuttaja, asianhoitaja, pinta_ala, geometria
            into tallentajaHist, tyostaVastaavaHist, tyonSuorittajaHist, rakennuttajaHist, asianhoitajaHist, pintaAlaHist, geomHist
        from johtoselvitys_historia where hakemuksen_id=NEW.id and dml_id=(select max(dml_id) from johtoselvitys_historia where hakemuksen_id=NEW.id);
        -- Check if any of the fields have changed
        IF (
            OLD.applicationidentifier IS DISTINCT FROM NEW.applicationidentifier OR
            OLD.allustatus IS DISTINCT FROM NEW.allustatus OR
            OLD.applicationdata ->> ''name'' IS DISTINCT FROM NEW.applicationdata ->> ''name'' OR
            trim(OLD.applicationdata -> ''postalAddress'' -> ''streetAddress'' ->> ''streetName'') IS DISTINCT FROM trim(NEW.applicationdata -> ''postalAddress'' -> ''streetAddress'' ->> ''streetName'') OR
            OLD.applicationdata ->> ''constructionWork'' IS DISTINCT FROM NEW.applicationdata ->> ''constructionWork'' OR
            OLD.applicationdata ->> ''maintenanceWork'' IS DISTINCT FROM NEW.applicationdata ->> ''maintenanceWork'' OR
            OLD.applicationdata ->> ''propertyConnectivity'' IS DISTINCT FROM NEW.applicationdata ->> ''propertyConnectivity'' OR
            OLD.applicationdata ->> ''emergencyWork'' IS DISTINCT FROM NEW.applicationdata ->> ''emergencyWork'' OR
            OLD.applicationdata ->> ''rockExcavation'' IS DISTINCT FROM NEW.applicationdata ->> ''rockExcavation'' OR
            OLD.applicationdata ->> ''workDescription'' IS DISTINCT FROM NEW.applicationdata ->> ''workDescription'' OR
            OLD.applicationdata -> ''startTime'' IS DISTINCT FROM NEW.applicationdata -> ''startTime'' OR
            OLD.applicationdata -> ''endTime'' IS DISTINCT FROM NEW.applicationdata -> ''endTime'' OR
            tyostaVastaavaNew IS DISTINCT FROM tyostaVastaavaHist OR
            tyonSuorittajaNew IS DISTINCT FROM tyonSuorittajaHist OR
            rakennuttajaNew IS DISTINCT FROM rakennuttajaHist OR
            asianhoitajaNew IS DISTINCT FROM asianhoitajaHist OR
            NOT(tallentajaNew @> tallentajaHist and tallentajaNew <@ tallentajaHist) OR
            pintaAlaNew IS DISTINCT FROM pintaAlaHist OR
            NOT(ST_Equals(geomNew,geomHist))
            ) THEN
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
            pinta_ala,
            geometria,
            muutosaika,
            tallentaja_taho,
            dml_type, dml_timestamp, dml_created_by)
        VALUES (
            (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
            NEW.id, NEW.applicationidentifier, NEW.allustatus,
            NEW.applicationdata ->> ''name'',
            trim(NEW.applicationdata -> ''postalAddress'' -> ''streetAddress'' ->> ''streetName''),
            NEW.applicationdata ->> ''constructionWork'',
            NEW.applicationdata ->> ''maintenanceWork'',
            NEW.applicationdata ->> ''propertyConnectivity'',
            NEW.applicationdata ->> ''emergencyWork'',
            NEW.applicationdata ->> ''rockExcavation'',
            NEW.applicationdata ->> ''workDescription'',
            to_timestamp(cast(NEW.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(NEW.applicationdata ->> ''endTime'' as decimal)),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end AS rakennuttajat from hakemusyhteystieto where application_id=NEW.id and rooli=''HAKIJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end AS rakennuttajat from hakemusyhteystieto where application_id=NEW.id and rooli=''TYON_SUORITTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end AS rakennuttajat from hakemusyhteystieto where application_id=NEW.id and rooli=''RAKENNUTTAJA''),
            (select case when tyyppi=''PERSON'' then ''YKSITYISHENKILO'' else nimi end AS rakennuttajat from hakemusyhteystieto where application_id=NEW.id and rooli=''ASIANHOITAJA''),
            pintaAlaNew,
            geomNew,
            CURRENT_TIMESTAMP,
            (select array_agg(h4.tyyppi) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
            ''UPDATE'', CURRENT_TIMESTAMP, CURRENT_USER);
        END IF;
    ELSIF (TG_OP = ''DELETE'' AND OLD.applicationtype=''CABLE_REPORT'') THEN
        -- Handle delete logic here
        select hh.* into oldData from johtoselvitys_historia hh where hh.dml_id=(select max(dml_id) from johtoselvitys_historia where hakemuksen_id=OLD.id);
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
            pinta_ala,
            geometria,
            muutosaika,
            tallentaja_taho,
            dml_type, dml_timestamp, dml_created_by)
        VALUES (
            oldData.hanketunnus,
            OLD.id, OLD.applicationidentifier, OLD.allustatus,
            OLD.applicationdata ->> ''name'',
            trim(OLD.applicationdata -> ''postalAddress'' -> ''streetAddress'' ->> ''streetName''),
            OLD.applicationdata ->> ''constructionWork'',
            OLD.applicationdata ->> ''maintenanceWork'',
            OLD.applicationdata ->> ''propertyConnectivity'',
            OLD.applicationdata ->> ''emergencyWork'',
            OLD.applicationdata ->> ''rockExcavation'',
            OLD.applicationdata ->> ''workDescription'',
            to_timestamp(cast(OLD.applicationdata ->> ''startTime'' as decimal)),
            to_timestamp(cast(OLD.applicationdata ->> ''endTime'' as decimal)),
            oldData.tyosta_vastaava,
            oldData.tyon_suorittaja,
            oldData.rakennuttaja,
            oldData.asianhoitaja,
            oldData.pinta_ala,
            oldData.geometria,
            CURRENT_TIMESTAMP,
            oldData.tallentaja_taho,
            ''DELETE'', CURRENT_TIMESTAMP, CURRENT_USER);
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_johtoselvitys_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON applications
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_johtoselvitys_changes();