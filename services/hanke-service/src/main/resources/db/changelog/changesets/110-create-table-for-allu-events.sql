--liquibase formatted sql
--changeset Teemu Hiltunen:110-create-table-for-allu-events
--comment: Create table for Allu events

CREATE TABLE allu_event
(
    id                      bigserial                NOT NULL PRIMARY KEY,
    allu_id                 int                      NOT NULL,
    event_time              timestamp with time zone NOT NULL,
    new_status              varchar                  NOT NULL,
    application_identifier  varchar                  NOT NULL,
    target_status           varchar,
    status                  varchar                  NOT NULL DEFAULT 'PENDING',
    stacktrace              text,
    created_at              timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at            timestamp with time zone,
    retry_count             int                      NOT NULL DEFAULT 0,
    CONSTRAINT uk_allu_event_alluid_eventtime_newstatus UNIQUE (allu_id, event_time, new_status),
    CONSTRAINT chk_allu_event_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_allu_event_alluid ON allu_event (allu_id);
CREATE INDEX idx_allu_event_applicationidentifier ON allu_event (application_identifier);
CREATE INDEX idx_allu_event_status ON allu_event (status);
CREATE INDEX idx_allu_event_status_alluid ON allu_event (status, allu_id);
CREATE INDEX idx_allu_event_status_applicationidentifier ON allu_event (status, application_identifier);

COMMENT ON TABLE allu_event is 'Allu history events error table.';
COMMENT ON COLUMN allu_event.allu_id is 'The unique Allu identifier for the application.';
COMMENT ON COLUMN allu_event.event_time is 'Allu event time.';
COMMENT ON COLUMN allu_event.new_status is 'The new status of the application after the event.';
COMMENT ON COLUMN allu_event.application_identifier is 'The application identifier.';
COMMENT ON COLUMN allu_event.target_status is 'Tells next status (DECISION, OPERATIONAL_CONDITION or FINISHED) if current status is DECISIONMAKING';
COMMENT ON COLUMN allu_event.status is 'The status of the event.';
COMMENT ON COLUMN allu_event.stacktrace is 'Stack trace of the error that occurred during the event handling.';
COMMENT ON COLUMN allu_event.created_at is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN allu_event.processed_at is 'Timestamp of when this event was successfully processed.';
COMMENT ON COLUMN allu_event.retry_count is 'Number of times this event has been retried in case of an error.';

-- Insert existing error records as FAILED events
INSERT INTO allu_event (
    allu_id,
    event_time,
    new_status,
    application_identifier,
    target_status,
    status,
    stacktrace,
    created_at,
    processed_at,
    retry_count
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
ON CONFLICT (allu_id, event_time, new_status) DO NOTHING;

-- Drop the old table
DROP TABLE allu_event_error;
