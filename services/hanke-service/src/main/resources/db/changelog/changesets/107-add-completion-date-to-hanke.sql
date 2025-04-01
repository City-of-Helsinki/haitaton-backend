--liquibase formatted sql
--changeset Topias Heinonen:107-add-completion-date-to-hanke
--comment: Add column for tracing hanke completion date

ALTER TABLE hanke
    ADD COLUMN completedat timestamptz DEFAULT NULL;
