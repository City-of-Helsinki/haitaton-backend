--liquibase formatted sql
--changeset Topias Heinonen:108-add-sent-reminders-to-hanke
--comment: Add sent_reminders to Hanke

ALTER TABLE hanke
    ADD COLUMN sent_reminders character varying[] NOT NULL DEFAULT '{}';
