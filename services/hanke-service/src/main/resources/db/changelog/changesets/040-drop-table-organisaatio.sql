--liquibase formatted sql
--changeset Topias Heinonen:040-drop-table-organisaatio
--comment: Drop unused table organisaatio

DROP TABLE organisaatio;
DROP SEQUENCE IF EXISTS organisaatio_id_seq;
