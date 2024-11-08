--liquibase formatted sql
--changeset Teemu Hiltunen:086-change-car-lane-nuisance-enum
--comment: Change 'hankealue.kaistahaitta' enum values and 'tormaystarkastelutulos.kaistapituushaitta' and 'applications.applicationData.areas[n].tyoalueet[m].tormaystarkastelutulos.kaistapituushaitta' index values

-- update hankealue.kaistahaitta enum values
UPDATE hankealue
    SET kaistahaitta =
        CASE
            WHEN kaistahaitta = 'VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA' THEN 'YKSI_KAISTA_VAHENEE'
            WHEN kaistahaitta = 'VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA' THEN 'YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA'
            WHEN kaistahaitta = 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA' THEN 'USEITA_KAISTOJA_VAHENEE_AJOSUUNNILLA'
            WHEN kaistahaitta = 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA' THEN 'USEITA_AJOSUUNTIA_POISTUU_KAYTOSTA'
            ELSE kaistahaitta
        END;

-- update tormaystarkastelutulos.kaistapituushaitta
UPDATE tormaystarkastelutulos
    SET kaistapituushaitta=0
    WHERE kaistapituushaitta=1;

-- update tormaystarkastelutulos.kaistahaitta
UPDATE tormaystarkastelutulos
    SET kaistahaitta =
        CASE kaistahaitta
            WHEN 1 THEN 0
            WHEN 2 THEN 1
            WHEN 3 THEN 2
            WHEN 4 THEN 3
            ELSE kaistahaitta
        END;

-- update applications.applicationData.areas
WITH areas_array AS (
    SELECT id,
        CASE
            WHEN (applicationData #>> '{areas}') IS NOT NULL AND jsonb_typeof(applicationData->'areas') = 'array' THEN
                jsonb_set(
                    applicationData,
                    '{areas}',
                    (
                        SELECT jsonb_agg(
                            CASE
                                WHEN (area #>> '{tyoalueet}') IS NOT NULL AND jsonb_typeof(area->'tyoalueet') = 'array' THEN
                                    -- Update area level kaistahaitta first
                                    jsonb_set(
                                        jsonb_set(
                                            area,
                                            '{tyoalueet}',
                                            (
                                                SELECT jsonb_agg(
                                                    CASE
                                                        WHEN (tyoalue #>> '{tormaystarkasteluTulos}') IS NOT NULL AND
                                                            (tyoalue #>> '{tormaystarkasteluTulos,autoliikenne}') IS NOT NULL THEN
                                                            CASE
                                                                -- First update kaistapituushaitta if it's 1
                                                                WHEN tyoalue->'tormaystarkasteluTulos'->'autoliikenne'->>'kaistapituushaitta' = '1' THEN
                                                                    jsonb_set(
                                                                        jsonb_set(
                                                                            tyoalue,
                                                                            '{tormaystarkasteluTulos,autoliikenne,kaistapituushaitta}',
                                                                            '0'::jsonb
                                                                        ),
                                                                        '{tormaystarkasteluTulos,autoliikenne,kaistahaitta}',
                                                                        (
                                                                            CASE tyoalue->'tormaystarkasteluTulos'->'autoliikenne'->>'kaistahaitta'
                                                                                WHEN '1' THEN '0'::jsonb
                                                                                WHEN '2' THEN '1'::jsonb
                                                                                WHEN '3' THEN '2'::jsonb
                                                                                WHEN '4' THEN '3'::jsonb
                                                                                WHEN '5' THEN '5'::jsonb
                                                                                ELSE tyoalue->'tormaystarkasteluTulos'->'autoliikenne'->'kaistahaitta'
                                                                            END
                                                                        )
                                                                    )
                                                                ELSE
                                                                    -- Only update kaistahaitta
                                                                    jsonb_set(
                                                                        tyoalue,
                                                                        '{tormaystarkasteluTulos,autoliikenne,kaistahaitta}',
                                                                        (
                                                                            CASE tyoalue->'tormaystarkasteluTulos'->'autoliikenne'->>'kaistahaitta'
                                                                                WHEN '1' THEN '0'::jsonb
                                                                                WHEN '2' THEN '1'::jsonb
                                                                                WHEN '3' THEN '2'::jsonb
                                                                                WHEN '4' THEN '3'::jsonb
                                                                                WHEN '5' THEN '5'::jsonb
                                                                                ELSE tyoalue->'tormaystarkasteluTulos'->'autoliikenne'->'kaistahaitta'
                                                                            END
                                                                        )
                                                                    )
                                                            END
                                                        ELSE tyoalue
                                                    END
                                                )
                                                FROM jsonb_array_elements(area->'tyoalueet') tyoalue
                                            )
                                        ),
                                        '{kaistahaitta}',
                                        (
                                            CASE area->>'kaistahaitta'
                                                WHEN 'VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA' THEN
                                                    '"YKSI_KAISTA_VAHENEE"'::jsonb
                                                WHEN 'VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA' THEN
                                                    '"YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA"'::jsonb
                                                WHEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA' THEN
                                                    '"USEITA_KAISTOJA_VAHENEE_AJOSUUNNILLA"'::jsonb
                                                WHEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA' THEN
                                                    '"USEITA_AJOSUUNTIA_POISTUU_KAYTOSTA"'::jsonb
                                                ELSE area->'kaistahaitta'
                                            END
                                        )
                                    )
                                ELSE area
                            END
                        )
                        FROM jsonb_array_elements(applicationData->'areas') area
                    )
                )
            ELSE applicationData
        END as updated_data
    FROM applications
)
UPDATE applications
SET applicationData = areas_array.updated_data
FROM areas_array
WHERE applications.id = areas_array.id;
