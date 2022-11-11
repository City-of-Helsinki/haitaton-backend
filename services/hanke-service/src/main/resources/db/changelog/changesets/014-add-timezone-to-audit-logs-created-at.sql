--liquibase formatted sql
--changeset Topias Heinonen:014-add-timezone-to-audit-logs-created-at
--comment: Add timezone to audit_logs created_at

ALTER TABLE audit_logs
    ALTER created_at TYPE timestamp with time zone USING created_at AT TIME ZONE 'UTC',
    ALTER created_at SET DEFAULT now();
