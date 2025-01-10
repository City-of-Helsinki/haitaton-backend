--liquibase formatted sql
--changeset Petri Timlin:089-add-trigger-adding-hanke_historia-lines-for-hanke
--comment: Add trigger for checking changes of hanke and inserting rows to hanke_historia

CREATE FUNCTION check_hanke_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    alkupvm date;
    loppupvm date;
    alkupvmHist date;
    loppupvmHist date;
    tyomaatyypit text[];
    tyomaatyypitHist text[];
    omistajat text[];
    omistajatHist text[];
    rakennuttajat text[];
    rakennuttajatHist text[];
    toteuttajat text[];
    toteuttajatHist text[];
    muut text[];
    muutHist text[];
    tallentaja text[];
    tallentajaHist text[];
  BEGIN
    IF (TG_OP = ''INSERT'') THEN
      -- Handle insert logic here
      INSERT INTO hanke_historia (hanke_id, hanketunnus, hankkeen_nimi, hankkeen_kuvaus, katuosoite,
          hankkeen_alkupaiva, hankkeen_loppupaiva, vaihe,
          tyon_tyyppi, ykt_hanke,
          omistaja_nimi,
          rakennuttaja_nimi,
          tyon_suorittaja_nimi,
          muu_taho,
          muutosaika,
          tallentaja_taho,
          dml_type, dml_timestamp, dml_created_by)
        VALUES (NEW.id, NEW.hanketunnus, NEW.nimi, NEW.kuvaus, NEW.tyomaakatuosoite,
          (select min(haittaalkupvm) from hankealue where hankeid=NEW.id), (select max(haittaloppupvm) from hankealue where hankeid=NEW.id), NEW.vaihe,
          (select array_agg(tyomaatyyppi) AS tyomaatyypit FROM hanketyomaatyyppi h where hankeid=NEW.id), NEW.onykthanke,
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS omistajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''OMISTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS rakennuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''RAKENNUTTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS toteuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''TOTEUTTAJA''),
          (select array_agg(rooli) AS muut from hankeyhteystieto where hankeid=NEW.id and contacttype=''MUU''),
          COALESCE(NEW.modifiedat,NEW.createdat),
          (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.id),
          ''INSERT'', CURRENT_TIMESTAMP, CURRENT_USER);
    ELSIF (TG_OP = ''UPDATE'') THEN
      -- Handle update logic here
      select min(haittaalkupvm) into alkupvm from hankealue where hankeid=NEW.id;
      select max(haittaloppupvm) into loppupvm from hankealue where hankeid=NEW.id;
      select coalesce(array_agg(tyomaatyyppi),array[]::text[]) into tyomaatyypit FROM hanketyomaatyyppi h where hankeid=NEW.id;
      select coalesce(array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end),array[]::text[]) into omistajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''OMISTAJA'';
      select coalesce(array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end),array[]::text[]) into rakennuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''RAKENNUTTAJA'';
      select coalesce(array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end),array[]::text[]) into toteuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''TOTEUTTAJA'';
      select coalesce(array_agg(rooli),array[]::text[]) into muut from hankeyhteystieto where hankeid=NEW.id and contacttype=''MUU'';
      select coalesce(array_agg(h4.contacttype),array[]::text[]) into tallentaja from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.id;

      select hankkeen_alkupaiva, hankkeen_loppupaiva, coalesce(tyon_tyyppi,array[]::text[]), coalesce(omistaja_nimi,array[]::text[]), coalesce(rakennuttaja_nimi,array[]::text[]), coalesce(tyon_suorittaja_nimi,array[]::text[]), coalesce(muu_taho,array[]::text[]), coalesce(tallentaja_taho,array[]::text[]) into alkupvmHist, loppupvmHist, tyomaatyypitHist, omistajatHist, rakennuttajatHist, toteuttajatHist, muutHist, tallentajaHist from hanke_historia where hanke_id=NEW.id and dml_id=(select max(dml_id) from hanke_historia where hanke_id=NEW.id);

      -- Check if any of the fields have changed
      IF (OLD.id IS DISTINCT FROM NEW.id OR
          OLD.hanketunnus IS DISTINCT FROM NEW.hanketunnus OR
          OLD.nimi IS DISTINCT FROM NEW.nimi OR
          OLD.vaihe IS DISTINCT FROM NEW.vaihe OR
          OLD.onykthanke IS DISTINCT FROM NEW.onykthanke OR
          OLD.kuvaus IS DISTINCT FROM NEW.kuvaus OR
          OLD.tyomaakatuosoite IS DISTINCT FROM NEW.tyomaakatuosoite OR
          alkupvm<>alkupvmHist OR
          loppupvm<>loppupvmHist OR
          not(tyomaatyypit @> tyomaatyypitHist and tyomaatyypit <@ tyomaatyypitHist) OR
          not(omistajat @> omistajatHist and omistajat <@ omistajatHist) OR
          not(rakennuttajat @> rakennuttajatHist and rakennuttajat <@ rakennuttajatHist) OR
          not(toteuttajat @> toteuttajatHist and toteuttajat <@ toteuttajatHist) OR
          not(muut @> muutHist and muut <@ muutHist) OR
          not(tallentaja @> tallentajaHist and tallentaja <@ tallentajaHist)
          ) THEN
      INSERT INTO hanke_historia (hanke_id, hanketunnus, hankkeen_nimi, hankkeen_kuvaus, katuosoite,
          hankkeen_alkupaiva, hankkeen_loppupaiva, vaihe,
          tyon_tyyppi, ykt_hanke,
          omistaja_nimi,
          rakennuttaja_nimi,
          tyon_suorittaja_nimi,
          muu_taho,
          muutosaika,
          tallentaja_taho,
          dml_type, dml_timestamp, dml_created_by)
        VALUES (NEW.id, NEW.hanketunnus, NEW.nimi, NEW.kuvaus, NEW.tyomaakatuosoite,
          (select min(haittaalkupvm) from hankealue where hankeid=NEW.id), (select max(haittaloppupvm) from hankealue where hankeid=NEW.id), NEW.vaihe,
          (SELECT array_agg(tyomaatyyppi) AS tyomaatyypit FROM hanketyomaatyyppi h where hankeid=NEW.id), NEW.onykthanke,
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS omistajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''OMISTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS rakennuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''RAKENNUTTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS toteuttajat from hankeyhteystieto where hankeid=NEW.id and contacttype=''TOTEUTTAJA''),
          (select array_agg(rooli) AS muut from hankeyhteystieto where hankeid=NEW.id and contacttype=''MUU''),
          COALESCE(NEW.modifiedat,NEW.createdat),
          (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.id),
          ''UPDATE'', CURRENT_TIMESTAMP, CURRENT_USER);
      END IF;
    ELSIF (TG_OP = ''DELETE'') THEN
      -- Handle delete logic here
      INSERT INTO hanke_historia (hanke_id, hanketunnus, hankkeen_nimi, hankkeen_kuvaus, katuosoite,
          hankkeen_alkupaiva, hankkeen_loppupaiva, vaihe,
          tyon_tyyppi, ykt_hanke,
          omistaja_nimi,
          rakennuttaja_nimi,
          tyon_suorittaja_nimi,
          muu_taho,
          muutosaika,
          tallentaja_taho,
          dml_type, dml_timestamp, dml_created_by)
        VALUES (OLD.id, OLD.hanketunnus, OLD.nimi, OLD.kuvaus, OLD.tyomaakatuosoite,
          (select min(haittaalkupvm) from hankealue where hankeid=OLD.id), (select max(haittaloppupvm) from hankealue where hankeid=OLD.id), OLD.vaihe,
          (SELECT array_agg(tyomaatyyppi) AS tyomaatyypit FROM hanketyomaatyyppi h where hankeid=OLD.id), OLD.onykthanke,
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS omistajat from hankeyhteystieto where hankeid=OLD.id and contacttype=''OMISTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS rakennuttajat from hankeyhteystieto where hankeid=OLD.id and contacttype=''RAKENNUTTAJA''),
          (select array_agg(case when tyyppi=''YKSITYISHENKILO'' then ''YKSITYISHENKILO'' else nimi end) AS toteuttajat from hankeyhteystieto where hankeid=OLD.id and contacttype=''TOTEUTTAJA''),
          (select array_agg(rooli) AS muut from hankeyhteystieto where hankeid=OLD.id and contacttype=''MUU''),
          COALESCE(OLD.modifiedat,OLD.createdat),
          (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=OLD.id),
          ''DELETE'', CURRENT_TIMESTAMP, CURRENT_USER);
    END IF;
    RETURN NEW;
  END;
';

CREATE TRIGGER after_hanke_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON hanke
    FOR EACH ROW
EXECUTE FUNCTION check_hanke_changes();