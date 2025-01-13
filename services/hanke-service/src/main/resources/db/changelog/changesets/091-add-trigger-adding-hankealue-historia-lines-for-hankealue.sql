--liquibase formatted sql
--changeset Petri Timlin:091-add-trigger-adding-hankealue-historia-lines-for-hankealue
--comment: Add trigger for checking changes of hankealue and inserting rows to hankealue_historia

CREATE FUNCTION check_hankealue_changes()
    RETURNS TRIGGER
    LANGUAGE PLPGSQL
AS '
  DECLARE
    pyoraliikenneNew integer;
    autoliikenneNew integer;
    linjaautoliikenneNew integer;
    raitioliikenneNew integer;
    geometriaNew geometry;
    muutosaikaNew timestamp;
    pyoraliikenneHist integer;
    autoliiikenneHist integer;
    linjaautoliikenneHist integer;
    raitioliikenneHist integer;
    geometriaHist geometry;
    muutosaikaHist timestamp;
    tallentaja text[];
    tallentajaHist text[];
  BEGIN
    IF (TG_OP = ''INSERT'') THEN
        -- Handle insert logic here
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
            NEW.id, NEW.hankeid,
            (select hanke.hanketunnus from hanke where id=NEW.hankeid), NEW.nimi,
            NEW.haittaalkupvm, NEW.haittaloppupvm,
            NEW.meluhaitta, NEW.polyhaitta, NEW.tarinahaitta, NEW.kaistahaitta, NEW.kaistapituushaitta,
            (select pyoraliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select autoliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select linjaautoliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select raitioliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select hankegeometria.geometria from hankegeometria where hankegeometriatid=NEW.geometriat),
            (select coalesce(hanke.modifiedat, hanke.createdat) from hanke where id=NEW.hankeid),
            (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.hankeid),
            ''INSERT'', CURRENT_TIMESTAMP, CURRENT_USER);
    ELSIF (TG_OP = ''UPDATE'') THEN
        -- Handle update logic here
        select coalesce(hanke.modifiedat, hanke.createdat) into muutosaikaNew from hanke where id=NEW.hankeid;
        select pyoraliikenne into pyoraliikenneNew from tormaystarkastelutulos where hankealue_id=NEW.id;
        select autoliikenne into autoliikenneNew from tormaystarkastelutulos where hankealue_id=NEW.id;
        select linjaautoliikenne into linjaautoliikenneNew from tormaystarkastelutulos where hankealue_id=NEW.id;
        select raitioliikenne into raitioliikenneNew from tormaystarkastelutulos where hankealue_id=NEW.id;
        select geometria into geometriaNew from hankegeometria where hankegeometriatid=NEW.geometriat;
        select coalesce(hanke.modifiedat, hanke.createdat) into muutosaikaNew from hanke where id=NEW.hankeid;
        select coalesce(array_agg(h4.contacttype),array[]::text[]) into tallentaja from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.hankeid;

        select haittaindeksi_pyoraliikenne, haittaindeksi_autoliikenne, haittaindeksi_linja_autojen_paikallisliikenne, haittaindeksi_raitioliikenne, hankealueen_geometria, coalesce(tallentaja_taho,array[]::text[]), muutosaika
            into pyoraliikenneHist, autoliiikenneHist, linjaautoliikenneHist, raitioliikenneHist, geometriaHist, tallentajaHist, muutosaikaHist
        from hankealue_historia where hankealue_id=NEW.id and dml_id=(select max(dml_id) from hankealue_historia where hankealue_id=NEW.id);

        -- Check if any of the fields have changed
        IF (
            OLD.nimi IS DISTINCT FROM NEW.nimi OR
            OLD.haittaalkupvm IS DISTINCT FROM NEW.haittaalkupvm OR
            OLD.haittaloppupvm IS DISTINCT FROM NEW.haittaloppupvm OR
            OLD.meluhaitta IS DISTINCT FROM NEW.meluhaitta OR
            OLD.polyhaitta IS DISTINCT FROM NEW.polyhaitta OR
            OLD.tarinahaitta IS DISTINCT FROM NEW.tarinahaitta OR
            OLD.kaistahaitta IS DISTINCT FROM NEW.kaistahaitta OR
            OLD.kaistapituushaitta IS DISTINCT FROM NEW.kaistapituushaitta OR
            pyoraliikenneNew<>pyoraliikenneHist OR
            autoliikenneNew<>autoliiikenneHist OR
            linjaautoliikenneNew<>linjaautoliikenneHist OR
            raitioliikenneNew<>raitioliikenneHist OR
            not(ST_Equals(geometriaNew,geometriaHist)) OR
            not(tallentaja @> tallentajaHist and tallentaja <@ tallentajaHist) OR
            muutosaikaNew<>muutosaikaHist
            ) THEN
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
            NEW.id, NEW.hankeid,
            (select hanke.hanketunnus from hanke where id=NEW.hankeid), NEW.nimi,
            NEW.haittaalkupvm, NEW.haittaloppupvm,
            NEW.meluhaitta, NEW.polyhaitta, NEW.tarinahaitta, NEW.kaistahaitta, NEW.kaistapituushaitta,
            (select pyoraliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select autoliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select linjaautoliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select raitioliikenne from tormaystarkastelutulos where hankealue_id=NEW.id),
            (select hankegeometria.geometria from hankegeometria where hankegeometriatid=NEW.geometriat),
            (select coalesce(hanke.modifiedat, hanke.createdat) from hanke where id=NEW.hankeid),
            (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=NEW.hankeid),
            ''UPDATE'', CURRENT_TIMESTAMP, CURRENT_USER);
        END IF;
    ELSIF (TG_OP = ''DELETE'') THEN
        -- Handle delete logic here
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
            OLD.id, OLD.hankeid,
            (select hanke.hanketunnus from hanke where id=OLD.hankeid), OLD.nimi,
            OLD.haittaalkupvm, OLD.haittaloppupvm,
            OLD.meluhaitta, OLD.polyhaitta, OLD.tarinahaitta, OLD.kaistahaitta, OLD.kaistapituushaitta,
            (select pyoraliikenne from tormaystarkastelutulos where hankealue_id=OLD.id),
            (select autoliikenne from tormaystarkastelutulos where hankealue_id=OLD.id),
            (select linjaautoliikenne from tormaystarkastelutulos where hankealue_id=OLD.id),
            (select raitioliikenne from tormaystarkastelutulos where hankealue_id=OLD.id),
            (select hankegeometria.geometria from hankegeometria where hankegeometriatid=OLD.geometriat),
            (select coalesce(hanke.modifiedat, hanke.createdat) from hanke where id=OLD.hankeid),
            (select array_agg(h4.contacttype) from hanke h left join hankekayttaja h2 on (h.id=h2.hanke_id and h2.kutsuja_id is null) left join hankeyhteyshenkilo h3 on h2.id=h3.hankekayttaja_id left join hankeyhteystieto h4 on h3.hankeyhteystieto_id=h4.id where h.id=OLD.hankeid),
            ''DELETE'', CURRENT_TIMESTAMP, CURRENT_USER);
    END IF;
    RETURN NEW;
  END;
';

CREATE CONSTRAINT TRIGGER after_hankealue_changes
    AFTER INSERT OR UPDATE OR DELETE
    ON hankealue
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW
EXECUTE FUNCTION check_hankealue_changes();