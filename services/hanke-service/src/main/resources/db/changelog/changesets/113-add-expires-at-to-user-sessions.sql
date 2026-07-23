--liquibase formatted sql
--changeset haitaton:113-add-expires-at-to-user-sessions
--comment: Add expires_at column to user_sessions table for JWT expiration-based cleanup

ALTER TABLE user_sessions
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX idx_user_sessions_expires_at ON user_sessions (expires_at);
