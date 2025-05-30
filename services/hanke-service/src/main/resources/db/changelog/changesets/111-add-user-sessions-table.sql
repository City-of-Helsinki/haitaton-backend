--liquibase formatted sql
--changeset Teemu Hiltunen:110-add-user-sessions-table
--comment: Add user_sessions table for implementing back-channel logout

CREATE TABLE user_sessions
(
    id         UUID                     NOT NULL PRIMARY KEY,
    subject    VARCHAR(255)             NOT NULL,
    session_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_sessions_subject ON user_sessions (subject);
CREATE INDEX idx_user_sessions_session_id ON user_sessions (session_id);

-- Add unique constraint for subject + session_id (used for conflict resolution)
ALTER TABLE user_sessions
    ADD CONSTRAINT uq_user_sessions_subject_sid UNIQUE (subject, session_id);
