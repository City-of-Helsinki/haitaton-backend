--liquibase formatted sql
--changeset Teemu Hiltunen:111-drop-table-for-allu-event-errors
--comment: Copy data from Allu event error table to Allu event table and drop table for Allu event errors

-- Insert existing error records as FAILED events
INSERT INTO allu_event (
    alluid,
    eventtime,
    newstatus,
    applicationidentifier,
    targetstatus,
    status,
    stacktrace,
    createdat,
    processedat,
    retrycount
)
SELECT
    alluid,
    eventtime,
    newstatus,
    applicationidentifier,
    targetstatus,
    'FAILED',
    stacktrace,
    createdat,
    NULL,  -- processedat (not processed)
    0      -- retrycount (reset)
FROM allu_event_error
ON CONFLICT (alluid, eventtime, newstatus) DO NOTHING;

-- Drop the old table
DROP TABLE allu_event_error;
