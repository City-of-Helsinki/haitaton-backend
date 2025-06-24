--liquibase formatted sql
--changeset Teemu Hiltunen:109-create-table-for-allu-event-errors
--comment: Create table for Allu events

CREATE TABLE allu_event_error
(
    id                      serial                   NOT NULL PRIMARY KEY,
    alluid                  int                      NOT NULL,
    eventtime               timestamp                NOT NULL,
    newstatus               varchar                  NOT NULL,
    applicationidentifier   varchar                  NOT NULL,
    targetstatus            varchar,
    stacktrace              text,
    createdat               timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_allu_event_error_alluid_eventtime UNIQUE (alluid, eventtime)
);

CREATE INDEX idx_allu_event_error_alluid ON allu_event_error (alluid);
CREATE INDEX idx_allu_event_error_applicationidentifier ON allu_event_error (applicationidentifier);

COMMENT ON TABLE allu_event_error is 'Allu history events error table.';
COMMENT ON COLUMN allu_event_error.alluid is 'The unique Allu identifier for the application.';
COMMENT ON COLUMN allu_event_error.eventtime is 'Allu event time.';
COMMENT ON COLUMN allu_event_error.newstatus is 'The new status of the application after the event.';
COMMENT ON COLUMN allu_event_error.applicationidentifier is 'The application identifier.';
COMMENT ON COLUMN allu_event_error.targetstatus is 'Tells next status (DECISION, OPERATIONAL_CONDITION or FINISHED) if current status is DECISIONMAKING';
COMMENT ON COLUMN allu_event_error.stacktrace is 'Stack trace of the error that occurred during the event handling.';
COMMENT ON COLUMN allu_event_error.createdat is 'Timestamp of when this row was initially created.';
