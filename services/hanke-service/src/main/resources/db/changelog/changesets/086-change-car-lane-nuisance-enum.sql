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

-- update applications.applicationData.areas
WITH updated_areas AS (
    SELECT id,
        CASE
            WHEN (applicationData #>> '{areas}') IS NOT NULL AND jsonb_typeof(applicationData->'areas') = 'array' THEN
                jsonb_set(
                    applicationData,
                    '{areas}',
                    jsonb_agg(
                        CASE
                            WHEN (area #>> '{kaistahaitta}') IS NOT NULL THEN
                                jsonb_set(
                                    area,
                                    '{kaistahaitta}',
                                    CASE area->>'kaistahaitta'
                                        WHEN 'VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA' THEN '"YKSI_KAISTA_VAHENEE"'::jsonb
                                        WHEN 'VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA' THEN '"YKSI_KAISTA_VAHENEE_KAHDELLA_AJOSUUNNALLA"'::jsonb
                                        WHEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_KAHDELLA_AJOSUUNNALLA' THEN '"USEITA_KAISTOJA_VAHENEE_AJOSUUNNILLA"'::jsonb
                                        WHEN 'VAHENTAA_SAMANAIKAISESTI_USEITA_KAISTOJA_LIITTYMIEN_ERI_SUUNNILLA' THEN '"USEITA_AJOSUUNTIA_POISTUU_KAYTOSTA"'::jsonb
                                        ELSE area->'kaistahaitta'
                                    END
                                )
                            ELSE area
                        END
                    )
                )
            ELSE applicationData
        END AS updated_data
    FROM applications,
         jsonb_array_elements(applicationData->'areas') AS area
    WHERE applicationData IS NOT NULL
    GROUP BY id, applicationData
)

UPDATE applications
SET applicationData = updated_areas.updated_data
    FROM updated_areas
WHERE applications.id = updated_areas.id;
