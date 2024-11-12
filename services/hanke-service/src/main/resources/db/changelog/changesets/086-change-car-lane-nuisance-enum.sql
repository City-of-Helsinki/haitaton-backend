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
WITH areas_array AS (
    SELECT id,
        CASE
            WHEN (applicationData #>> '{areas}') IS NOT NULL AND jsonb_typeof(applicationData->'areas') = 'array' THEN
                jsonb_set(
                    applicationData,
                    '{areas}',
                    (
                        SELECT jsonb_agg(
                            jsonb_set(
                                area,
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
