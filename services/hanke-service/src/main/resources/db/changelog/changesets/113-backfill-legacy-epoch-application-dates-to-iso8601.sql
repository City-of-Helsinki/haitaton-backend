--liquibase formatted sql
--changeset Claude:113-backfill-legacy-epoch-application-dates-to-iso8601
--comment: Changeset 112 made the historia triggers assume applicationdata's startTime/endTime are
--comment: ISO-8601 strings, matching what Spring Boot 4's Jackson 3 now writes. Rows saved before
--comment: that change still hold these fields as epoch-second numbers, which the new triggers can't
--comment: cast to timestamptz. Convert those legacy values to ISO-8601 in applications, taydennys and
--comment: muutosilmoitus so INSERT/UPDATE/DELETE on old rows doesn't fail the historia triggers.

/* Disable all three historia triggers on applications (johtoselvitys and kaivuilmoitus from
   changesets 095/100, kaivuilmoitusalueet from changeset 105) while backfilling: the UPDATE below
   would otherwise fire johtoselvitys's trigger and have it try to cast the (still epoch-formatted)
   OLD.applicationdata using the fixed trigger logic from changeset 112, failing on the very rows
   this migration is trying to fix. All three must be disabled, not just the two that reference
   startTime/endTime: these are DEFERRABLE INITIALLY DEFERRED constraint triggers, and Postgres
   refuses any ALTER TABLE on a table with a pending (not yet committed) deferred trigger event,
   even for a trigger the ALTER TABLE isn't targeting and even if that other trigger is a no-op for
   this data - confirmed by reproducing "cannot ALTER TABLE ... because it has pending trigger
   events" with only two of the three disabled. Liquibase's formatted-SQL parser treats any
   "--"-prefixed line in the body as an attempted directive, so plain SQL comments here must use
   block-comment syntax instead. */
ALTER TABLE applications
    DISABLE TRIGGER after_johtoselvitys_changes;
ALTER TABLE applications
    DISABLE TRIGGER after_kaivuilmoitus_changes;
ALTER TABLE applications
    DISABLE TRIGGER after_kaivuilmoitusalueet_changes;

UPDATE applications
SET applicationdata =
        applicationdata
            || jsonb_build_object(
                'startTime',
                CASE
                    WHEN applicationdata ->> 'startTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((applicationdata ->> 'startTime')::decimal))
                    ELSE applicationdata -> 'startTime'
                    END,
                'endTime',
                CASE
                    WHEN applicationdata ->> 'endTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((applicationdata ->> 'endTime')::decimal))
                    ELSE applicationdata -> 'endTime'
                    END
               )
WHERE applicationdata ->> 'startTime' ~ '^\d+(\.\d+)?$'
   OR applicationdata ->> 'endTime' ~ '^\d+(\.\d+)?$';

ALTER TABLE applications
    ENABLE TRIGGER after_johtoselvitys_changes;
ALTER TABLE applications
    ENABLE TRIGGER after_kaivuilmoitus_changes;
ALTER TABLE applications
    ENABLE TRIGGER after_kaivuilmoitusalueet_changes;

/* taydennys and muutosilmoitus copy applicationdata from applications but have no historia
   triggers of their own, so no need to disable anything before updating them. */
UPDATE taydennys
SET application_data =
        application_data
            || jsonb_build_object(
                'startTime',
                CASE
                    WHEN application_data ->> 'startTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((application_data ->> 'startTime')::decimal))
                    ELSE application_data -> 'startTime'
                    END,
                'endTime',
                CASE
                    WHEN application_data ->> 'endTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((application_data ->> 'endTime')::decimal))
                    ELSE application_data -> 'endTime'
                    END
               )
WHERE application_data ->> 'startTime' ~ '^\d+(\.\d+)?$'
   OR application_data ->> 'endTime' ~ '^\d+(\.\d+)?$';

UPDATE muutosilmoitus
SET application_data =
        application_data
            || jsonb_build_object(
                'startTime',
                CASE
                    WHEN application_data ->> 'startTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((application_data ->> 'startTime')::decimal))
                    ELSE application_data -> 'startTime'
                    END,
                'endTime',
                CASE
                    WHEN application_data ->> 'endTime' ~ '^\d+(\.\d+)?$'
                        THEN to_jsonb(to_timestamp((application_data ->> 'endTime')::decimal))
                    ELSE application_data -> 'endTime'
                    END
               )
WHERE application_data ->> 'startTime' ~ '^\d+(\.\d+)?$'
   OR application_data ->> 'endTime' ~ '^\d+(\.\d+)?$';
