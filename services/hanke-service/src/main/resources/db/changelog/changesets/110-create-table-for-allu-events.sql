--liquibase formatted sql
--changeset Teemu Hiltunen:110-create-table-for-allu-events
--comment: Create table for Allu events

CREATE TABLE allu_event
(
    id                      bigserial                NOT NULL PRIMARY KEY,
    alluid                  int                      NOT NULL,
    eventtime               timestamp with time zone NOT NULL,
    newstatus               varchar                  NOT NULL,
    applicationidentifier   varchar                  NOT NULL,
    targetstatus            varchar,
    status                  varchar                  NOT NULL DEFAULT 'PENDING',
    stacktrace              text,
    createdat               timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processedat             timestamp with time zone,
    retrycount              int                      NOT NULL DEFAULT 0,
    CONSTRAINT uk_allu_event_alluid_eventtime_newstatus UNIQUE (alluid, eventtime, newstatus),
    CONSTRAINT chk_allu_event_status CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED'))
);

CREATE INDEX idx_allu_event_alluid ON allu_event (alluid);
CREATE INDEX idx_allu_event_applicationidentifier ON allu_event (applicationidentifier);
CREATE INDEX idx_allu_event_status ON allu_event (status);
CREATE INDEX idx_allu_event_status_alluid ON allu_event (status, alluid);
CREATE INDEX idx_allu_event_status_applicationidentifier ON allu_event (status, applicationidentifier);

COMMENT ON TABLE allu_event is 'Allu history events error table.';
COMMENT ON COLUMN allu_event.alluid is 'The unique Allu identifier for the application.';
COMMENT ON COLUMN allu_event.eventtime is 'Allu event time.';
COMMENT ON COLUMN allu_event.newstatus is 'The new status of the application after the event.';
COMMENT ON COLUMN allu_event.applicationidentifier is 'The application identifier.';
COMMENT ON COLUMN allu_event.targetstatus is 'Tells next status (DECISION, OPERATIONAL_CONDITION or FINISHED) if current status is DECISIONMAKING';
COMMENT ON COLUMN allu_event.status is 'The status of the event.';
COMMENT ON COLUMN allu_event.stacktrace is 'Stack trace of the error that occurred during the event handling.';
COMMENT ON COLUMN allu_event.createdat is 'Timestamp of when this row was initially created.';
COMMENT ON COLUMN allu_event.processedat is 'Timestamp of when this event was successfully processed.';
COMMENT ON COLUMN allu_event.retrycount is 'Number of times this event has been retried in case of an error.';
