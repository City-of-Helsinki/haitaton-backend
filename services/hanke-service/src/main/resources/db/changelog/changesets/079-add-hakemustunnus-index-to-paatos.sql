--liquibase formatted sql
--changeset Topias Heinonen:079-add-hakemustunnus-index-to-paatos
--comment: Add an index to paatos, to make finding decisions by application identifier efficient.

CREATE INDEX idx_paatos_hakemustunnus on paatos (hakemustunnus);
