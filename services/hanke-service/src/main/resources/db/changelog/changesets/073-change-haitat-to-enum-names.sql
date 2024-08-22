--liquibase formatted sql
--changeset Topias Heinonen:073-change-haitat-to-enum-names
--comment: Change the haitat in hankealue to use enum names instead of order numbers.

ALTER TABLE hankealue
    ADD COLUMN meluhaitta_temp         VARCHAR,
    ADD COLUMN polyhaitta_temp         VARCHAR,
    ADD COLUMN tarinahaitta_temp       VARCHAR,
    ADD COLUMN kaistahaitta_temp       VARCHAR,
    ADD COLUMN kaistapituushaitta_temp VARCHAR
;

UPDATE hankealue
SET meluhaitta_temp         = CASE
                                  WHEN meluhaitta = 0 THEN 'SATUNNAINEN_MELUHAITTA'
                                  WHEN meluhaitta = 1 THEN 'TOISTUVA_MELUHAITTA'
                                  WHEN meluhaitta = 2 THEN 'JATKUVA_MELUHAITTA'
    END,
    polyhaitta_temp         = CASE
                                  WHEN polyhaitta = 0 THEN 'SATUNNAINEN_POLYHAITTA'
                                  WHEN polyhaitta = 1 THEN 'TOISTUVA_POLYHAITTA'
                                  WHEN polyhaitta = 2 THEN 'JATKUVA_POLYHAITTA'
        END,
    tarinahaitta_temp       = CASE
                                  WHEN tarinahaitta = 0 THEN 'SATUNNAINEN_TARINAHAITTA'
                                  WHEN tarinahaitta = 1 THEN 'TOISTUVA_TARINAHAITTA'
                                  WHEN tarinahaitta = 2 THEN 'JATKUVA_TARINAHAITTA'
        END,
    kaistahaitta_temp       = CASE
                                  WHEN kaistahaitta = 0 THEN 'EI_VAIKUTA'
                                  WHEN kaistahaitta = 1 THEN 'VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA'
                                  WHEN kaistahaitta = 2 THEN 'VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA'
                                  WHEN kaistahaitta = 3
                                      THEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA'
                                  WHEN kaistahaitta = 4
                                      THEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA'
        END,
    kaistapituushaitta_temp = CASE
                                  WHEN kaistapituushaitta = 0 THEN 'EI_VAIKUTA_KAISTAJARJESTELYIHIN'
                                  WHEN kaistapituushaitta = 1 THEN 'PITUUS_ALLE_10_METRIA'
                                  WHEN kaistapituushaitta = 2 THEN 'PITUUS_10_99_METRIA'
                                  WHEN kaistapituushaitta = 3 THEN 'PITUUS_100_499_METRIA'
                                  WHEN kaistapituushaitta = 4 THEN 'PITUUS_500_METRIA_TAI_ENEMMAN'
        END
;

ALTER TABLE hankealue
    DROP COLUMN meluhaitta,
    DROP COLUMN polyhaitta,
    DROP COLUMN tarinahaitta,
    DROP COLUMN kaistahaitta,
    DROP COLUMN kaistapituushaitta
;

ALTER TABLE hankealue
    RENAME COLUMN meluhaitta_temp TO meluhaitta;
ALTER TABLE hankealue
    RENAME COLUMN polyhaitta_temp TO polyhaitta;
ALTER TABLE hankealue
    RENAME COLUMN tarinahaitta_temp TO tarinahaitta;
ALTER TABLE hankealue
    RENAME COLUMN kaistahaitta_temp TO kaistahaitta;
ALTER TABLE hankealue
    RENAME COLUMN kaistapituushaitta_temp TO kaistapituushaitta;
