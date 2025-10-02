--liquibase formatted sql
--changeset Teemu Hiltunen:112-add-terminated-to-user-sessions
--comment: Add terminated column to user_sessions table to mark terminated sessions instead of deleting them

ALTER TABLE user_sessions
    ADD COLUMN terminated BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_user_sessions_terminated ON user_sessions (terminated);
