--liquibase formatted sql
--changeset Petri Timlin:104-add-trigger-adding-kaivuilmoitusalueet-historia-lines-for-applications
--comment: Add trigger for checking changes of kaivuilmoituselueet and inserting rows to kaivuilmoitusalueet_historia

CREATE FUNCTION check_kaivuilmoitusalueet_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    tallentajaNew text[];
    tallentajaHist text[];
    toiminnallisen_kunnon_ilmoitettu_paivaNew timestamp;
    toiminnallisen_kunnon_ilmoitettu_paivaHist timestamp;
    tyo_valmis_ilmoitettu_paivaNew timestamp;
    tyo_valmis_ilmoitettu_paivaHist timestamp;
    oldData kaivuilmoitusalueet_historia%rowtype;
    hankeArea jsonb;
    tyoArea jsonb;
    rowCount integer;
    rowId integer;
    rowIdDelete integer;
    hankeId integer;
    myPath jsonpath;
    hankeAreaGeom geometry;
    haittaindeksi_pyora decimal[];
    haittaindeksi_auto decimal[];
    haittaindeksi_linja_auto decimal[];
    haittaindeksi_raitioliikenne decimal[];
  BEGIN
    IF (TG_OP = ''INSERT'' AND NEW.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle insert logic here
        FOR hankeArea IN SELECT * FROM jsonb_array_elements(jsonb_strip_nulls(NEW.applicationdata)->''areas'')
        LOOP
            if jsonb_strip_nulls(hankeArea)->''hankealueId'' is not null then
                hankeAreaGeom := null;
                haittaindeksi_pyora := array[]::decimal[];
                haittaindeksi_auto := array[]::decimal[];
                haittaindeksi_linja_auto := array[]::decimal[];
                haittaindeksi_raitioliikenne := array[]::decimal[];
                FOR tyoArea IN SELECT * FROM jsonb_array_elements(jsonb_strip_nulls(hankeArea)->''tyoalueet'')
                LOOP
                    hankeAreaGeom := ST_Collect(hankeAreaGeom,ST_GeomFromGeoJSON(tyoArea->''geometry''));
                    haittaindeksi_pyora := array_append(haittaindeksi_pyora,(tyoArea->''tormaystarkasteluTulos''->''pyoraliikenneindeksi'')::decimal);
                    haittaindeksi_auto := array_append(haittaindeksi_auto,(tyoArea->''tormaystarkasteluTulos''->''autoliikenne''->''indeksi'')::decimal);
                    haittaindeksi_linja_auto := array_append(haittaindeksi_linja_auto,(tyoArea->''tormaystarkasteluTulos''->''linjaautoliikenneindeksi'')::decimal);
                    haittaindeksi_raitioliikenne := array_append(haittaindeksi_raitioliikenne,(tyoArea->''tormaystarkasteluTulos''->''raitioliikenneindeksi'')::decimal);
                END LOOP;
                INSERT INTO kaivuilmoitusalueet_historia (
                    hanketunnus,
                    hakemuksen_id,
                    liittyvan_hankealueen_id,
                    katuosoite,
                    tyon_tarkoitus,
                    meluhaitta,
                    polyhaitta,
                    tarinahaitta,
                    autoliikenteen_kaistahaitta,
                    kaistahaittojen_pituus,
                    pinta_ala,
                    haittaindeksi_pyoraliikenne,
                    haittaindeksi_autoliikenne,
                    haittaindeksi_linja_autojen_paikallisliikenne,
                    haittaindeksi_raitioliikenne,
                    tyoalueen_geometria,
                    muutosaika,
                    tallentaja_taho,
                    dml_type,
                    dml_timestamp,
                    dml_created_by,
                    hankealue_json_as_text)
                VALUES (
                    (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
                    NEW.id,
                    (hankeArea->>''hankealueId'')::integer,
                    hankeArea->>''katuosoite'',
                    array(select jsonb_array_elements_text(hankeArea->''tyonTarkoitukset'')),
                    hankeArea->>''meluhaitta'',
                    hankeArea->>''polyhaitta'',
                    hankeArea->>''tarinahaitta'',
                    hankeArea->>''kaistahaitta'',
                    hankeArea->>''kaistahaittojenPituus'',
                    round(st_area(ST_CollectionExtract(hankeAreaGeom))),
                    (select max(x) from unnest(haittaindeksi_pyora) as x),
                    (select max(x) from unnest(haittaindeksi_auto) as x),
                    (select max(x) from unnest(haittaindeksi_linja_auto) as x),
                    (select max(x) from unnest(haittaindeksi_raitioliikenne) as x),
                    ST_CollectionExtract(hankeAreaGeom),
                    CURRENT_TIMESTAMP,
                    (select array_agg(h4.rooli) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
                    ''INSERT'', CURRENT_TIMESTAMP, CURRENT_USER,
                    hankeArea::text);
            end if;
        END LOOP;
    ELSIF (TG_OP = ''UPDATE'' AND NEW.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle update logic here
        if (jsonb_strip_nulls(OLD.applicationdata)->>''areas'' IS DISTINCT FROM jsonb_strip_nulls(NEW.applicationdata)->>''areas'') then
            FOR hankeArea IN SELECT * FROM jsonb_array_elements(jsonb_strip_nulls(NEW.applicationdata)->''areas'')
            LOOP
                if jsonb_strip_nulls(hankeArea)->''hankealueId'' is not null then
                    oldData := null;
                    rowId := null;
                    select hh.dml_id into rowId from kaivuilmoitusalueet_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitusalueet_historia where hakemuksen_id=NEW.id and liittyvan_hankealueen_id=(hankeArea->>''hankealueId'')::integer);
                    select hh.dml_id into rowIdDelete from kaivuilmoitusalueet_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitusalueet_historia where hakemuksen_id=NEW.id and liittyvan_hankealueen_id=(hankeArea->>''hankealueId'')::integer and dml_type=''DELETE_HANKEALUE'');
                    select hh.* into oldData from kaivuilmoitusalueet_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitusalueet_historia where hakemuksen_id=NEW.id and liittyvan_hankealueen_id=(hankeArea->>''hankealueId'')::integer);
                    if (oldData.hankealue_json_as_text IS DISTINCT FROM hankeArea::text OR rowCount=0) then
                        hankeAreaGeom := null;
                        haittaindeksi_pyora := array[]::decimal[];
                        haittaindeksi_auto := array[]::decimal[];
                        haittaindeksi_linja_auto := array[]::decimal[];
                        haittaindeksi_raitioliikenne := array[]::decimal[];
                        FOR tyoArea IN SELECT * FROM jsonb_array_elements(jsonb_strip_nulls(hankeArea)->''tyoalueet'')
                        LOOP
                            hankeAreaGeom := ST_Collect(hankeAreaGeom,ST_GeomFromGeoJSON(tyoArea->''geometry''));
                            haittaindeksi_pyora := array_append(haittaindeksi_pyora,(tyoArea->''tormaystarkasteluTulos''->''pyoraliikenneindeksi'')::decimal);
                            haittaindeksi_auto := array_append(haittaindeksi_auto,(tyoArea->''tormaystarkasteluTulos''->''autoliikenne''->''indeksi'')::decimal);
                            haittaindeksi_linja_auto := array_append(haittaindeksi_linja_auto,(tyoArea->''tormaystarkasteluTulos''->''linjaautoliikenneindeksi'')::decimal);
                            haittaindeksi_raitioliikenne := array_append(haittaindeksi_raitioliikenne,(tyoArea->''tormaystarkasteluTulos''->''raitioliikenneindeksi'')::decimal);
                        END LOOP;
                        INSERT INTO kaivuilmoitusalueet_historia (
                            hanketunnus,
                            hakemuksen_id,
                            liittyvan_hankealueen_id,
                            katuosoite,
                            tyon_tarkoitus,
                            meluhaitta,
                            polyhaitta,
                            tarinahaitta,
                            autoliikenteen_kaistahaitta,
                            kaistahaittojen_pituus,
                            pinta_ala,
                            haittaindeksi_pyoraliikenne,
                            haittaindeksi_autoliikenne,
                            haittaindeksi_linja_autojen_paikallisliikenne,
                            haittaindeksi_raitioliikenne,
                            tyoalueen_geometria,
                            muutosaika,
                            tallentaja_taho,
                            dml_type,
                            dml_timestamp,
                            dml_created_by,
                            hankealue_json_as_text)
                        VALUES (
                            (select hanke.hanketunnus from hanke where id=NEW.hanke_id),
                            NEW.id,
                            (hankeArea->>''hankealueId'')::integer,
                            hankeArea->>''katuosoite'',
                            array(select jsonb_array_elements_text(hankeArea->''tyonTarkoitukset'')),
                            hankeArea->>''meluhaitta'',
                            hankeArea->>''polyhaitta'',
                            hankeArea->>''tarinahaitta'',
                            hankeArea->>''kaistahaitta'',
                            hankeArea->>''kaistahaittojenPituus'',
                            round(st_area(ST_CollectionExtract(hankeAreaGeom))),
                            (select max(x) from unnest(haittaindeksi_pyora) as x),
                            (select max(x) from unnest(haittaindeksi_auto) as x),
                            (select max(x) from unnest(haittaindeksi_linja_auto) as x),
                            (select max(x) from unnest(haittaindeksi_raitioliikenne) as x),
                            ST_CollectionExtract(hankeAreaGeom),
                            CURRENT_TIMESTAMP,
                            (select array_agg(h4.rooli) from hankekayttaja h2 left join hakemusyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hakemusyhteystieto h4 on h3.hakemusyhteystieto_id=h4.id where h4.application_id=NEW.id),
                            case when rowId=rowIdDelete or rowId is null then ''INSERT''
                                else ''UPDATE'' end,
                            CURRENT_TIMESTAMP, CURRENT_USER,
                            hankeArea::text);
                    end if;
                end if;
            END LOOP;
            FOR hankeId in select distinct liittyvan_hankealueen_id from kaivuilmoitusalueet_historia hh where hh.hakemuksen_id=NEW.id and hh.dml_type<>''DELETE_HANKEALUE''
            LOOP
                myPath := ''$.areas ? (@.hankealueId[*] == ''||hankeId||'')'';
                select jsonb_array_length(jsonb_path_query_array(NEW.applicationdata, myPath)) into rowCount;
                if rowCount=0 then
                    select hh.* into oldData from kaivuilmoitusalueet_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitusalueet_historia where hakemuksen_id=NEW.id and liittyvan_hankealueen_id=hankeId);
                    if oldData.dml_type<>''DELETE_HANKEALUE'' then
                        INSERT INTO kaivuilmoitusalueet_historia (
                            hanketunnus,
                            hakemuksen_id,
                            liittyvan_hankealueen_id,
                            katuosoite,
                            tyon_tarkoitus,
                            meluhaitta,
                            polyhaitta,
                            tarinahaitta,
                            autoliikenteen_kaistahaitta,
                            kaistahaittojen_pituus,
                            pinta_ala,
                            haittaindeksi_pyoraliikenne,
                            haittaindeksi_autoliikenne,
                            haittaindeksi_linja_autojen_paikallisliikenne,
                            haittaindeksi_raitioliikenne,
                            tyoalueen_geometria,
                            muutosaika,
                            tallentaja_taho,
                            dml_type,
                            dml_timestamp,
                            dml_created_by,
                            hankealue_json_as_text)
                        VALUES (
                            oldData.hanketunnus,
                            NEW.id,
                            oldData.liittyvan_hankealueen_id,
                            oldData.katuosoite,
                            oldData.tyon_tarkoitus,
                            oldData.meluhaitta,
                            oldData.polyhaitta,
                            oldData.tarinahaitta,
                            oldData.autoliikenteen_kaistahaitta,
                            oldData.kaistahaittojen_pituus,
                            oldData.pinta_ala,
                            oldData.haittaindeksi_pyoraliikenne,
                            oldData.haittaindeksi_autoliikenne,
                            oldData.haittaindeksi_linja_autojen_paikallisliikenne,
                            oldData.haittaindeksi_raitioliikenne,
                            oldData.tyoalueen_geometria,
                            CURRENT_TIMESTAMP,
                            oldData.tallentaja_taho,
                            ''DELETE_HANKEALUE'', CURRENT_TIMESTAMP, CURRENT_USER,
                            oldData.hankealue_json_as_text);
                    end if;
                end if;
            END LOOP;
        end if;
    ELSIF (TG_OP = ''DELETE'' AND OLD.applicationtype=''EXCAVATION_NOTIFICATION'') THEN
        -- Handle delete logic here
        FOR hankeArea IN SELECT * FROM jsonb_array_elements(jsonb_strip_nulls(OLD.applicationdata)->''areas'')
        LOOP
            if jsonb_strip_nulls(hankeArea)->''hankealueId'' is not null then
                select hh.* into oldData from kaivuilmoitusalueet_historia hh where hh.dml_id=(select max(dml_id) from kaivuilmoitusalueet_historia where hakemuksen_id=OLD.id and liittyvan_hankealueen_id=(hankeArea->>''hankealueId'')::integer);
                INSERT INTO kaivuilmoitusalueet_historia (
                    hanketunnus,
                    hakemuksen_id,
                    liittyvan_hankealueen_id,
                    katuosoite,
                    tyon_tarkoitus,
                    meluhaitta,
                    polyhaitta,
                    tarinahaitta,
                    autoliikenteen_kaistahaitta,
                    kaistahaittojen_pituus,
                    pinta_ala,
                    haittaindeksi_pyoraliikenne,
                    haittaindeksi_autoliikenne,
                    haittaindeksi_linja_autojen_paikallisliikenne,
                    haittaindeksi_raitioliikenne,
                    tyoalueen_geometria,
                    muutosaika,
                    tallentaja_taho,
                    dml_type,
                    dml_timestamp,
                    dml_created_by,
                    hankealue_json_as_text)
                VALUES (
                    oldData.hanketunnus,
                    OLD.id,
                    (hankeArea->>''hankealueId'')::integer,
                    hankeArea->>''katuosoite'',
                    array(select jsonb_array_elements_text(hankeArea->''tyonTarkoitukset'')),
                    hankeArea->>''meluhaitta'',
                    hankeArea->>''polyhaitta'',
                    hankeArea->>''tarinahaitta'',
                    hankeArea->>''kaistahaitta'',
                    hankeArea->>''kaistahaittojenPituus'',
                    oldData.pinta_ala,
                    oldData.haittaindeksi_pyoraliikenne,
                    oldData.haittaindeksi_autoliikenne,
                    oldData.haittaindeksi_linja_autojen_paikallisliikenne,
                    oldData.haittaindeksi_raitioliikenne,
                    oldData.tyoalueen_geometria,
                    CURRENT_TIMESTAMP,
                    oldData.tallentaja_taho,
                    ''DELETE'', CURRENT_TIMESTAMP, CURRENT_USER,
                    hankeArea::text);
            end if;
        END LOOP;
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_kaivuilmoitusalueet_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON applications
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_kaivuilmoitusalueet_changes();